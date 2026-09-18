package org.zmy.observabilityplatform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.zmy.observabilityplatform.diagnosis.application.service.DiagnosisService;
import org.zmy.observabilityplatform.diagnosis.domain.event.DiagnosisCompletedEvent;
import org.zmy.observabilityplatform.diagnosis.interfaces.rest.response.DiagnosisTaskResponse;
import org.zmy.observabilityplatform.incident.interfaces.rest.response.IncidentResponse;
import org.zmy.observabilityplatform.logging.interfaces.rest.response.LogResponse;

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

        List<LogResponse> storedLogs = webClient.get()
                .uri(uri -> uri.path("/api/v1/logs").queryParam("service", "orders-service").build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(LogResponse.class)
                .returnResult().getResponseBody();
        assertThat(storedLogs).hasSize(4);
        assertThat(storedLogs).anySatisfy(log -> assertThat(log.getRawMessage()).contains("password=***"));
        assertThat(storedLogs).filteredOn(log -> "ERROR".equals(log.getLevel()))
                .allSatisfy(log -> assertThat(log.getAttributes().get("message").toString())
                        .contains("password=***"));
        assertThat(storedLogs).anySatisfy(log -> assertThat(log.getLevel()).isEqualTo("UNKNOWN"));

        webClient.post().uri("/api/v1/logs/batch")
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted();

        List<IncidentResponse> incidents = webClient.get().uri("/api/v1/incidents")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(IncidentResponse.class)
                .returnResult().getResponseBody();
        assertThat(incidents).hasSize(1);
        IncidentResponse incident = incidents.get(0);
        assertThat(incident.getService()).isEqualTo("orders-service");
        assertThat(incident.getErrorCount()).isEqualTo(3);

        DiagnosisTaskResponse task = webClient.post()
                .uri("/api/v1/incidents/{incidentId}/diagnoses", incident.getId())
                .exchange()
                .expectStatus().isAccepted()
                .expectBody(DiagnosisTaskResponse.class)
                .returnResult().getResponseBody();
        assertThat(task).isNotNull();

        diagnosisService.complete(new DiagnosisCompletedEvent(
                UUID.randomUUID().toString(), task.getId(), incident.getId(), task.getVersion(),
                "Database connectivity is unavailable", 0.91,
                List.of("Three connection timeouts share one fingerprint"),
                List.of("Verify database connectivity and pool saturation"),
                List.of("IncidentContextService.GetIncidentContext(" + incident.getId() + ")"),
                Instant.now(), null)).block();

        webClient.get().uri("/api/v1/diagnoses/{taskId}", task.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.task.status").isEqualTo("SUCCEEDED")
                .jsonPath("$.report.confidence").isEqualTo(0.91)
                .jsonPath("$.report.evidence[0]").isEqualTo("Three connection timeouts share one fingerprint")
                .jsonPath("$.report.toolCalls[0]").exists();

        webClient.post().uri("/api/v1/incidents/{incidentId}/diagnoses", incident.getId())
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
