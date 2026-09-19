package org.zmy.observabilityplatform.integration.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.zmy.observabilityplatform.logging.domain.model.LogEntry;
import org.zmy.observabilityplatform.logging.infrastructure.repository.elasticsearch.ElasticsearchLogRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Testcontainers
class ElasticsearchInfrastructureIT {
    private static final String DATA_STREAM = "logs-observability-it";
    private static final String TEMPLATE = "observability-logs-it-template";
    private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Container
    private static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.5.4"))
            .withEnv("xpack.security.enabled", "false")
            .withEnv("xpack.security.http.ssl.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

    private static ElasticsearchLogRepository repository;
    private static WebClient client;

    @BeforeAll
    static void createRepository() {
        String url = "http://" + ELASTICSEARCH.getHttpHostAddress();
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        repository = new ElasticsearchLogRepository(
                WebClient.builder(), objectMapper, url, DATA_STREAM, TEMPLATE, "30d");
        client = WebClient.builder().baseUrl(url).build();
    }

    @Test
    void createsDataStreamWritesIdempotentlyAndFindsCompleteTrace() {
        Instant timestamp = Instant.parse("2026-09-20T10:00:00Z");
        LogEntry root = entry("log-root", "span-root", null, timestamp, "request accepted");
        LogEntry child = entry("log-child", "span-child", "span-root",
                timestamp.plusMillis(20), "database timeout");

        List<LogEntry> firstWrite = repository.saveAll(List.of(root, child)).block(TIMEOUT);
        List<LogEntry> duplicateWrite = repository.saveAll(List.of(root, child)).block(TIMEOUT);
        client.post().uri("/{dataStream}/_refresh", DATA_STREAM)
                .retrieve().toBodilessEntity().block(TIMEOUT);

        List<LogEntry> trace = repository.findByTraceId(TRACE_ID, 20)
                .collectList().block(TIMEOUT);
        JsonNode dataStream = client.get().uri("/_data_stream/{dataStream}", DATA_STREAM)
                .retrieve().bodyToMono(JsonNode.class).block(TIMEOUT);
        JsonNode template = client.get().uri("/_index_template/{template}", TEMPLATE)
                .retrieve().bodyToMono(JsonNode.class).block(TIMEOUT);

        assertThat(firstWrite).containsExactly(root, child);
        assertThat(duplicateWrite).isEmpty();
        assertThat(trace).extracting(LogEntry::getId).containsExactly("log-child", "log-root");
        assertThat(dataStream.path("data_streams")).hasSize(1);
        assertThat(template.toString())
                .contains("\"data_stream\":{}")
                .contains("\"data_retention\":\"30d\"")
                .contains("\"traceId\":{\"type\":\"keyword\"}");
    }

    private LogEntry entry(String id, String spanId, String parentSpanId,
                           Instant timestamp, String message) {
        return LogEntry.create(id, "batch-it", timestamp, timestamp, "orders", "integration",
                parentSpanId == null ? "INFO" : "ERROR", TRACE_ID, spanId, parentSpanId,
                "request-it", parentSpanId == null ? "POST /orders" : "SELECT orders",
                parentSpanId == null ? "SERVER" : "CLIENT", parentSpanId == null ? 202 : 504,
                parentSpanId == null, parentSpanId == null ? null : "DATABASE_TIMEOUT",
                parentSpanId == null ? 15L : 1200L, message, message,
                parentSpanId == null ? "fp-root" : "fp-timeout", Map.of("region", "test"));
    }
}
