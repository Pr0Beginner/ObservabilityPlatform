package org.zmy.observabilityplatform.logging.infrastructure.repository.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.zmy.observabilityplatform.logging.application.query.LogCursor;
import org.zmy.observabilityplatform.logging.application.query.LogSearchQuery;
import org.zmy.observabilityplatform.logging.domain.model.LogEntry;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElasticsearchLogRepositoryTest {
    private static final String DATA_STREAM = "logs-observability-default";
    private static final String TEMPLATE = "observability-logs-template";

    private final AtomicReference<String> templateRequest = new AtomicReference<>();
    private final AtomicReference<String> bulkRequest = new AtomicReference<>();
    private final AtomicReference<String> searchRequest = new AtomicReference<>();

    private DisposableServer server;
    private ElasticsearchLogRepository repository;

    @BeforeEach
    void setUp() {
        String timestamp = "2026-09-18T08:30:00Z";
        server = HttpServer.create()
                .port(0)
                .route(routes -> routes
                        .put("/_index_template/" + TEMPLATE, (request, response) -> request.receive()
                                .aggregate().asString()
                                .flatMap(body -> {
                                    templateRequest.set(body);
                                    return response.header("Content-Type", "application/json")
                                            .sendString(Mono.just("{\"acknowledged\":true}"))
                                            .then();
                                }))
                        .post("/" + DATA_STREAM + "/_bulk", (request, response) -> request.receive()
                                .aggregate().asString()
                                .flatMap(body -> {
                                    bulkRequest.set(body);
                                    String result = "{\"errors\":true,\"items\":["
                                            + "{\"create\":{\"status\":201}},"
                                            + "{\"create\":{\"status\":409,\"error\":{"
                                            + "\"reason\":\"document already exists\"}}}]}";
                                    return response.header("Content-Type", "application/json")
                                            .sendString(Mono.just(result))
                                            .then();
                                }))
                        .post("/" + DATA_STREAM + "/_search", (request, response) -> request.receive()
                                .aggregate().asString()
                                .flatMap(body -> {
                                    searchRequest.set(body);
                                    String result = "{\"hits\":{\"hits\":[{\"_source\":{"
                                            + "\"@timestamp\":\"" + timestamp + "\","
                                            + "\"id\":\"log-1\",\"batchId\":\"batch-1\","
                                            + "\"timestamp\":\"" + timestamp + "\","
                                            + "\"receivedAt\":\"" + timestamp + "\","
                                            + "\"service\":\"orders\",\"environment\":\"test\","
                                            + "\"level\":\"ERROR\",\"traceId\":\"trace-1\","
                                            + "\"spanId\":\"span-1\",\"parentSpanId\":\"span-0\","
                                            + "\"requestId\":\"request-1\",\"operation\":\"POST /orders\","
                                            + "\"spanKind\":\"SERVER\",\"statusCode\":504,\"success\":false,"
                                            + "\"errorCode\":\"TIMEOUT\",\"durationMs\":1200,"
                                            + "\"rawMessage\":\"timeout\",\"message\":\"timeout\","
                                            + "\"fingerprint\":\"fp-1\",\"attributes\":{}}}]}}";
                                    return response.header("Content-Type", "application/json")
                                            .sendString(Mono.just(result))
                                            .then();
                                })))
                .bindNow();

        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        repository = new ElasticsearchLogRepository(WebClient.builder(), objectMapper,
                "http://localhost:" + server.port(), DATA_STREAM, TEMPLATE, "30d");
    }

    @AfterEach
    void tearDown() {
        server.disposeNow();
    }

    @Test
    void retriesTemplateInstallationAfterTemporaryFailure() {
        AtomicInteger templateCalls = new AtomicInteger();
        AtomicInteger bulkCalls = new AtomicInteger();
        var builder = WebClient.builder().exchangeFunction(request -> {
            if (request.url().getPath().startsWith("/_index_template")) {
                return Mono.just(ClientResponse.create(templateCalls.incrementAndGet() == 1
                        ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK).build());
            }
            bulkCalls.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
                    .body("{\"items\":[{\"create\":{\"status\":201}}]}").build());
        });
        var repo = new ElasticsearchLogRepository(builder, JsonMapper.builder().findAndAddModules().build(),
                "http://unused.invalid", DATA_STREAM, TEMPLATE, "30d");
        Instant now = Instant.parse("2026-09-22T12:00:00Z");
        LogEntry entry = LogEntry.create("log", "batch", now, now, "orders", "test", "INFO", "trace",
                null, null, null, null, null, null, null, null, null, "ok", "ok", "fp", Map.of());

        assertThatThrownBy(() -> repo.saveAll(List.of(entry)).block()).isInstanceOf(RuntimeException.class);
        assertThat(repo.saveAll(List.of(entry)).block()).containsExactly(entry);
        repo.saveAll(List.of(entry)).block();
        assertThat(templateCalls).hasValue(2);
        assertThat(bulkCalls).hasValue(2);
    }

    @Test
    void installsTemplateAndUsesIdempotentDataStreamWrites() {
        Instant timestamp = Instant.parse("2026-09-18T08:30:00Z");
        LogEntry entry = LogEntry.create("log-1", "batch-1", timestamp, timestamp,
                "orders", "test", "ERROR", "trace-1", "span-1", "span-0", "request-1",
                "POST /orders", "SERVER", 504, false, "TIMEOUT", 1200L,
                "timeout", "timeout", "fp-1", Map.of());

        List<LogEntry> created = repository.saveAll(List.of(entry, entry))
                .block(Duration.ofSeconds(5));

        assertThat(created).containsExactly(entry);
        assertThat(templateRequest.get())
                .contains("\"data_stream\":{}")
                .contains("\"data_retention\":\"30d\"")
                .contains("\"@timestamp\":{\"type\":\"date\"}")
                .contains("\"spanId\":{\"type\":\"keyword\"}")
                .contains("\"durationMs\":{\"type\":\"long\"}");
        assertThat(bulkRequest.get())
                .contains("\"create\":{\"_id\":\"log-1\"}")
                .contains("\"@timestamp\":\"2026-09-18T08:30:00Z\"");

        List<LogEntry> found = repository.search(new LogSearchQuery(null, null,
                        "orders", "test", "ERROR", "trace-1", "span-1", "request-1", "timeout", null,
                        new LogCursor(timestamp.toEpochMilli(), "cursor-id"), 20))
                .collectList()
                .block(Duration.ofSeconds(5));

        assertThat(found).containsExactly(entry);
        assertThat(searchRequest.get())
                .contains("\"term\":{\"service\":\"orders\"}")
                .contains("\"term\":{\"spanId\":\"span-1\"}")
                .contains("\"term\":{\"requestId\":\"request-1\"}")
                .contains("\"multi_match\":{\"query\":\"timeout\"")
                .contains("\"@timestamp\":{\"order\":\"desc\"}")
                .contains("\"id\":{\"order\":\"desc\"}")
                .contains("\"search_after\":[" + timestamp.toEpochMilli() + ",\"cursor-id\"]");
    }
}
