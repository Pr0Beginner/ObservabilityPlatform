package org.zmy.observabilityplatform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.zmy.observabilityplatform.diagnosis.application.DiagnosisService;
import org.zmy.observabilityplatform.diagnosis.domain.DiagnosisCompletedEvent;
import org.zmy.observabilityplatform.diagnosis.domain.DiagnosisTask;
import org.zmy.observabilityplatform.incident.domain.Incident;
import org.zmy.observabilityplatform.logging.domain.LogEntry;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.adapters.mode=local",
        "app.grpc.enabled=false",
        "app.incident.error-threshold=3"
})
@AutoConfigureWebTestClient
class ObservabilityPlatformEndToEndTest {
    @Autowired
    private WebTestClient webClient;

    @Autowired
    private DiagnosisService diagnosisService;

    @Test
    void completesLogToIncidentToDiagnosisReportFlow() {
        String batchId = "e2e-" + UUID.randomUUID();
        Instant timestamp = Instant.now();
        List<Map<String, Object>> logs = List.of(
                jsonLog(timestamp, "database connection timed out after 1000ms password=secret"),
                jsonLog(timestamp, "database connection timed out after 2000ms password=secret"),
                jsonLog(timestamp, "database connection timed out after 3000ms password=secret"),
                Map.of("timestamp", timestamp.toString(), "content", "{broken-json", "format", "JSON")
        );
        Map<String, Object> request = Map.of(
                "batchId", batchId,
                "service", "orders-service",
                "environment", "test",
                "logs", logs
        );

        webClient.post().uri("/api/v1/logs/batch")
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.accepted").isEqualTo(4);

        List<LogEntry> storedLogs = webClient.get()
                .uri(uri -> uri.path("/api/v1/logs").queryParam("service", "orders-service").build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(LogEntry.class)
                .returnResult().getResponseBody();
        assertThat(storedLogs).hasSize(4);
        assertThat(storedLogs).anySatisfy(log -> assertThat(log.rawMessage()).contains("password=***"));
        assertThat(storedLogs).filteredOn(log -> "ERROR".equals(log.level()))
                .allSatisfy(log -> assertThat(log.attributes().get("message").toString()).contains("password=***"));
        assertThat(storedLogs).anySatisfy(log -> assertThat(log.level()).isEqualTo("UNKNOWN"));

        webClient.post().uri("/api/v1/logs/batch")
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted();

        List<Incident> incidents = webClient.get().uri("/api/v1/incidents")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Incident.class)
                .returnResult().getResponseBody();
        assertThat(incidents).hasSize(1);
        Incident incident = incidents.get(0);
        assertThat(incident.service()).isEqualTo("orders-service");
        assertThat(incident.errorCount()).isEqualTo(3);

        DiagnosisTask task = webClient.post()
                .uri("/api/v1/incidents/{incidentId}/diagnoses", incident.id())
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(DiagnosisTask.class)
                .returnResult().getResponseBody();
        assertThat(task).isNotNull();

        diagnosisService.complete(new DiagnosisCompletedEvent(
                UUID.randomUUID().toString(), task.id(), incident.id(), task.version(),
                "Database connectivity is unavailable", 0.91,
                List.of("Three connection timeouts share one fingerprint"),
                List.of("Verify database connectivity and pool saturation"),
                List.of("IncidentContextService.GetIncidentContext(" + incident.id() + ")"),
                Instant.now(), null)).block();

        webClient.get().uri("/api/v1/diagnoses/{taskId}", task.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.task.status").isEqualTo("SUCCEEDED")
                .jsonPath("$.report.confidence").isEqualTo(0.91)
                .jsonPath("$.report.evidence[0]").isEqualTo("Three connection timeouts share one fingerprint")
                .jsonPath("$.report.toolCalls[0]").exists();

        webClient.post().uri("/api/v1/incidents/{incidentId}/diagnoses", incident.id())
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.version").isEqualTo(2)
                .jsonPath("$.status").isEqualTo("PENDING");
    }

    private Map<String, Object> jsonLog(Instant timestamp, String message) {
        String content = "{\"timestamp\":\"" + timestamp + "\",\"level\":\"ERROR\","
                + "\"traceId\":\"trace-e2e\",\"message\":\"" + message + "\"}";
        return Map.of("timestamp", timestamp.toString(), "content", content, "format", "JSON");
    }
}
