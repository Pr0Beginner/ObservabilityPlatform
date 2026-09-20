package org.zmy.observabilityplatform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.zmy.observabilityplatform.diagnosis.application.service.DiagnosisService;
import org.zmy.observabilityplatform.diagnosis.domain.event.DiagnosisCompletedEvent;
import org.zmy.observabilityplatform.diagnosis.interfaces.rest.response.DiagnosisTaskResponse;
import org.zmy.observabilityplatform.incident.interfaces.rest.response.IncidentResponse;
import org.zmy.observabilityplatform.logging.interfaces.rest.response.LogResponse;
import org.zmy.observabilityplatform.shared.interfaces.rest.PageResponse;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.adapters.mode=local",
        "app.grpc.enabled=false",
        "app.security.enabled=false"
})
@AutoConfigureWebTestClient
class ObservabilityPlatformEndToEndTest {
    @Autowired
    private WebTestClient webClient;

    @Autowired
    private DiagnosisService diagnosisService;

    @Test
    void exposesPrometheusHttpMetrics() {
        webClient.get().uri("/api/v1/incidents?size=1").exchange().expectStatus().isOk();

        webClient.get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("http_server_requests_seconds_count"));
    }

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

        PageResponse<IncidentResponse> incidentPage = webClient.get().uri("/api/v1/incidents")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<PageResponse<IncidentResponse>>() { })
                .returnResult().getResponseBody();
        assertThat(incidentPage).isNotNull();
        List<IncidentResponse> incidents = incidentPage.getItems();
        assertThat(incidents).hasSize(1);
        IncidentResponse incident = incidents.get(0);
        assertThat(incident.getService()).isEqualTo("orders-service");
        assertThat(incident.getErrorCount()).isEqualTo(3);

        webClient.get().uri("/api/v1/incidents/{incidentId}/traces", incident.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].traceId").isEqualTo("trace-e2e")
                .jsonPath("$[0].unassignedLogs.length()").isEqualTo(3);

        webClient.get().uri("/api/v1/traces/{traceId}/incidents", "trace-e2e")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(incident.getId());

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

    @Test
    void reconstructsAServiceCallTreeByTraceId() {
        String traceId = "0af7651916cd43dd8448eb211c80319c";
        Instant startedAt = Instant.parse("2026-09-19T10:00:00Z");

        ingestTraceLog(traceId, "gateway", startedAt, "1111111111111111", null,
                "POST /orders", true, 200, null, 300);
        ingestTraceLog(traceId, "order-service", startedAt.plusMillis(20), "2222222222222222",
                "1111111111111111", "createOrder", true, 200, null, 250);
        ingestTraceLog(traceId, "payment-service", startedAt.plusMillis(50), "3333333333333333",
                "2222222222222222", "POST /payments", false, 504, "PAYMENT_TIMEOUT", 180);

        webClient.get().uri("/api/v1/traces/{traceId}", traceId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Trace-Id")
                .expectBody()
                .jsonPath("$.traceId").isEqualTo(traceId)
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.roots[0].service").isEqualTo("gateway")
                .jsonPath("$.roots[0].children[0].service").isEqualTo("order-service")
                .jsonPath("$.roots[0].children[0].children[0].service").isEqualTo("payment-service")
                .jsonPath("$.roots[0].children[0].children[0].errorCode").isEqualTo("PAYMENT_TIMEOUT");

        webClient.get().uri("/api/v1/requests/{requestId}/traces", "request-trace-e2e")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].traceId").isEqualTo(traceId);
    }

    @Test
    void managesScopedAnomalyPoliciesWithVersionProtection() {
        String observedService = "policy-e2e-" + UUID.randomUUID();
        Map<String, Object> request = anomalyPolicyRequest(observedService);

        Map<?, ?> created = webClient.post().uri("/api/v1/anomaly-policies")
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody();
        assertThat(created).isNotNull();
        String policyId = (String) created.get("id");

        webClient.get().uri("/api/v1/anomaly-policies/{policyId}", policyId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo(observedService)
                .jsonPath("$.scope").isEqualTo("SERVICE")
                .jsonPath("$.version").isEqualTo(1);

        request.put("name", "Disabled policy");
        request.put("enabled", false);
        request.put("version", 1);
        webClient.put().uri("/api/v1/anomaly-policies/{policyId}", policyId)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.enabled").isEqualTo(false)
                .jsonPath("$.version").isEqualTo(2);
    }

    private void ingestTraceLog(String traceId, String service, Instant timestamp, String spanId,
                                String parentSpanId, String operation, boolean success, int statusCode,
                                String errorCode, long durationMs) {
        Map<String, Object> content = new java.util.LinkedHashMap<>();
        content.put("timestamp", timestamp.toString());
        content.put("level", success ? "INFO" : "ERROR");
        content.put("message", operation + (success ? " completed" : " failed"));
        content.put("traceId", traceId);
        content.put("spanId", spanId);
        if (parentSpanId != null) {
            content.put("parentSpanId", parentSpanId);
        }
        content.put("requestId", "request-trace-e2e");
        content.put("operation", operation);
        content.put("spanKind", "SERVER");
        content.put("success", success);
        content.put("statusCode", statusCode);
        if (errorCode != null) {
            content.put("errorCode", errorCode);
        }
        content.put("durationMs", durationMs);
        String json;
        try {
            json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(content);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
        Map<String, Object> request = Map.of(
                "batchId", "trace-e2e-" + service + "-" + UUID.randomUUID(),
                "service", service,
                "environment", "test",
                "logs", List.of(Map.of("timestamp", timestamp.toString(), "content", json, "format", "JSON"))
        );
        webClient.post().uri("/api/v1/logs/batch")
                .header("traceparent", "00-" + traceId + "-aaaaaaaaaaaaaaaa-01")
                .bodyValue(request)
                .exchange()
                .expectStatus().isAccepted()
                .expectHeader().valueEquals("X-Trace-Id", traceId);
    }

    private Map<String, Object> jsonLog(Instant timestamp, String message) {
        String content = "{\"timestamp\":\"" + timestamp + "\",\"level\":\"ERROR\","
                + "\"traceId\":\"trace-e2e\",\"message\":\"" + message + "\"}";
        return Map.of("timestamp", timestamp.toString(), "content", content, "format", "JSON");
    }

    private Map<String, Object> anomalyPolicyRequest(String observedService) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "Service policy");
        request.put("scope", "SERVICE");
        request.put("service", observedService);
        request.put("environment", "test");
        request.put("enabled", true);
        request.put("errorThreshold", 3);
        request.put("minimumRequests", 20);
        request.put("minimumErrorCodeCount", 5);
        request.put("requestSpikeRatio", 2.0);
        request.put("requestDropRatio", 0.5);
        request.put("failureRateThreshold", 0.1);
        request.put("errorCodeRateThreshold", 0.05);
        request.put("baselineMultiplier", 2.0);
        request.put("comparisonPeriodMinutes", 1_440);
        request.put("recoveryWindows", 2);
        return request;
    }
}
