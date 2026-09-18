package org.zmy.observabilityplatform.logging.infrastructure.repository.opensearch;

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

import java.time.Instant;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class OpenSearchLogRepository implements LogRepository, LogQueryRepository {
    private static final MediaType NDJSON = MediaType.parseMediaType("application/x-ndjson");
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String index;

    public OpenSearchLogRepository(WebClient.Builder builder,
                                   ObjectMapper objectMapper,
                                   @Value("${app.opensearch.url}") String url,
                                   @Value("${app.opensearch.index}") String index) {
        this.webClient = builder.baseUrl(url).build();
        this.objectMapper = objectMapper;
        this.index = index;
    }

    @Override
    public Mono<List<LogEntry>> saveAll(List<LogEntry> entries) {
        if (entries.isEmpty()) {
            return Mono.just(List.of());
        }
        StringBuilder body = new StringBuilder();
        try {
            for (LogEntry entry : entries) {
                body.append("{\"index\":{\"_index\":\"").append(index)
                        .append("\",\"_id\":\"").append(entry.id()).append("\"}}\n");
                body.append(objectMapper.writeValueAsString(entry)).append('\n');
            }
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }
        return webClient.post().uri("/_bulk")
                .contentType(NDJSON)
                .bodyValue(body.toString())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(response -> response.path("errors").asBoolean(false)
                        ? Mono.error(new IllegalStateException("OpenSearch bulk request contains failed items"))
                        : Mono.just(newlyCreated(entries, response)));
    }

    @Override
    public Flux<LogEntry> search(LogSearchQuery query) {
        ObjectNode body = buildQuery(query);
        return webClient.post().uri("/{index}/_search", index)
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

    private ObjectNode buildQuery(LogSearchQuery query) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("size", query.size());
        root.putArray("sort").addObject().putObject("timestamp").put("order", "desc");
        ObjectNode bool = root.putObject("query").putObject("bool");
        ArrayNode filter = bool.putArray("filter");
        term(filter, "service.keyword", query.service());
        term(filter, "environment.keyword", query.environment());
        term(filter, "level.keyword", query.level());
        term(filter, "traceId.keyword", query.traceId());
        term(filter, "fingerprint.keyword", query.fingerprint());
        if (query.from() != null || query.to() != null) {
            ObjectNode range = filter.addObject().putObject("range").putObject("timestamp");
            if (query.from() != null) range.put("gte", query.from().toString());
            if (query.to() != null) range.put("lte", query.to().toString());
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
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
            return objectMapper.treeToValue(source, LogEntry.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot deserialize OpenSearch log", exception);
        }
    }

    private List<LogEntry> newlyCreated(List<LogEntry> entries, JsonNode response) {
        JsonNode items = response.path("items");
        if (!items.isArray() || items.size() != entries.size()) {
            throw new IllegalStateException("Unexpected OpenSearch bulk response");
        }
        java.util.ArrayList<LogEntry> created = new java.util.ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).path("index").path("status").asInt() == 201) {
                created.add(entries.get(index));
            }
        }
        return created;
    }
}
