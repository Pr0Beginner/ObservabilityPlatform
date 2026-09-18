package org.zmy.observabilityplatform.logging.infrastructure.repository.elasticsearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.WebClient;
import org.zmy.observabilityplatform.logging.application.query.LogQueryRepository;
import org.zmy.observabilityplatform.logging.application.query.LogSearchQuery;
import org.zmy.observabilityplatform.logging.domain.model.LogEntry;
import org.zmy.observabilityplatform.logging.domain.repository.LogRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class ElasticsearchLogRepository implements LogRepository, LogQueryRepository {
    private static final MediaType NDJSON = MediaType.parseMediaType("application/x-ndjson");

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String dataStream;
    private final String templateName;
    private final String retention;
    private final Mono<Void> templateInitialization;

    public ElasticsearchLogRepository(WebClient.Builder builder,
                                      ObjectMapper objectMapper,
                                      @Value("${app.elasticsearch.url}") String url,
                                      @Value("${app.elasticsearch.data-stream}") String dataStream,
                                      @Value("${app.elasticsearch.template}") String templateName,
                                      @Value("${app.elasticsearch.retention}") String retention) {
        this.webClient = builder.baseUrl(url).build();
        this.objectMapper = objectMapper;
        this.dataStream = dataStream;
        this.templateName = templateName;
        this.retention = retention;
        // 模板初始化结果被缓存，进程生命周期内只执行一次安装请求。
        this.templateInitialization = Mono.defer(this::installIndexTemplate).cache();
    }

    @Override
    public Mono<List<LogEntry>> saveAll(List<LogEntry> entries) {
        if (entries.isEmpty()) {
            return Mono.just(List.of());
        }
        return templateInitialization.then(Mono.defer(() -> bulkCreate(entries)));
    }

    @Override
    public Flux<LogEntry> search(LogSearchQuery query) {
        ObjectNode body = buildQuery(query);
        return webClient.post().uri("/{dataStream}/_search", dataStream)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchangeToMono(response -> {
                    if (response.statusCode().value() == 404) {
                        return Mono.empty();
                    }
                    if (response.statusCode().isError()) {
                        return response.createException().flatMap(Mono::error);
                    }
                    return response.bodyToMono(JsonNode.class);
                })
                .flatMapMany(this::mapHits);
    }

    @Override
    public Flux<LogEntry> findByIncidentContext(String service, String environment, String fingerprint, int limit) {
        return search(new LogSearchQuery(null, null, service, environment, null,
                null, null, fingerprint, limit));
    }

    private Mono<Void> installIndexTemplate() {
        ObjectNode root = objectMapper.createObjectNode();
        root.putArray("index_patterns").add(dataStream);
        root.put("priority", 500);
        root.putObject("data_stream");
        root.putObject("_meta").put("description", "Observability Platform log data stream");

        ObjectNode template = root.putObject("template");
        template.putObject("lifecycle").put("data_retention", retention);
        ObjectNode properties = template.putObject("mappings").putObject("properties");
        keyword(properties, "id");
        keyword(properties, "batchId");
        date(properties, "@timestamp");
        date(properties, "timestamp");
        date(properties, "receivedAt");
        keyword(properties, "service");
        keyword(properties, "environment");
        keyword(properties, "level");
        keyword(properties, "traceId");
        text(properties, "rawMessage");
        text(properties, "message");
        keyword(properties, "fingerprint");
        properties.putObject("attributes").put("type", "flattened");

        return webClient.put().uri("/_index_template/{templateName}", templateName)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(root)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .then();
    }

    private Mono<List<LogEntry>> bulkCreate(List<LogEntry> entries) {
        StringBuilder body = new StringBuilder();
        try {
            // 使用 create 和稳定文档 ID，批次重试时 Elasticsearch 会用 409 表示记录已存在。
            for (LogEntry entry : entries) {
                body.append("{\"create\":{\"_id\":\"").append(entry.id()).append("\"}}\n");
                ObjectNode source = objectMapper.valueToTree(entry);
                source.put("@timestamp", entry.timestamp().toString());
                body.append(objectMapper.writeValueAsString(source)).append('\n');
            }
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }
        return webClient.post().uri("/{dataStream}/_bulk", dataStream)
                .contentType(NDJSON)
                .bodyValue(body.toString())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> newlyCreated(entries, response));
    }

    private ObjectNode buildQuery(LogSearchQuery query) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("size", query.size());
        root.putArray("sort").addObject().putObject("@timestamp").put("order", "desc");
        ObjectNode bool = root.putObject("query").putObject("bool");
        ArrayNode filter = bool.putArray("filter");
        term(filter, "service", query.service());
        term(filter, "environment", query.environment());
        term(filter, "level", query.level());
        term(filter, "traceId", query.traceId());
        term(filter, "fingerprint", query.fingerprint());
        // 精确条件和时间范围进入 filter，避免无关的相关性评分开销。
        if (query.from() != null || query.to() != null) {
            ObjectNode range = filter.addObject().putObject("range").putObject("@timestamp");
            if (query.from() != null) {
                range.put("gte", query.from().toString());
            }
            if (query.to() != null) {
                range.put("lte", query.to().toString());
            }
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            // 关键词同时检索解析后消息和脱敏后的原始消息。
            ObjectNode match = bool.putArray("must").addObject().putObject("multi_match");
            match.put("query", query.keyword());
            match.putArray("fields").add("message").add("rawMessage");
        }
        return root;
    }

    private void term(ArrayNode filter, String field, String value) {
        if (value != null && !value.isBlank()) {
            filter.addObject().putObject("term").put(field, value);
        }
    }

    private Flux<LogEntry> mapHits(JsonNode response) {
        JsonNode hits = response.path("hits").path("hits");
        if (!hits.isArray()) {
            return Flux.empty();
        }
        return Flux.fromIterable(hits)
                .map(hit -> hit.path("_source"))
                .map(this::toEntry);
    }

    private LogEntry toEntry(JsonNode source) {
        try {
            ObjectNode copy = source.deepCopy();
            copy.remove("@timestamp");
            return objectMapper.treeToValue(copy, LogEntry.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot deserialize Elasticsearch log", exception);
        }
    }

    private List<LogEntry> newlyCreated(List<LogEntry> entries, JsonNode response) {
        JsonNode items = response.path("items");
        if (!items.isArray() || items.size() != entries.size()) {
            throw new IllegalStateException("Unexpected Elasticsearch bulk response");
        }
        List<LogEntry> created = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            JsonNode result = items.get(index).path("create");
            int status = result.path("status").asInt();
            // 409 表示幂等重试中的重复文档，不再进入后续事件计数。
            if (status == 201) {
                created.add(entries.get(index));
            } else if (status != 409) {
                String reason = result.path("error").path("reason").asText("unknown error");
                throw new IllegalStateException("Elasticsearch bulk item failed with status "
                        + status + ": " + reason);
            }
        }
        return created;
    }

    private void keyword(ObjectNode properties, String name) {
        properties.putObject(name).put("type", "keyword");
    }

    private void text(ObjectNode properties, String name) {
        properties.putObject(name).put("type", "text");
    }

    private void date(ObjectNode properties, String name) {
        properties.putObject(name).put("type", "date");
    }
}
