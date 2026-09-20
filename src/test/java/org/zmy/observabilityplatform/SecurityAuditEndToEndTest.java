package org.zmy.observabilityplatform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicyReference;
import org.zmy.observabilityplatform.incident.domain.model.Incident;
import org.zmy.observabilityplatform.incident.infrastructure.repository.memory.InMemoryIncidentRepository;

import java.time.Instant;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.adapters.mode=local",
        "app.grpc.enabled=false",
        "app.security.enabled=true",
        "app.security.read-only.username=test-viewer",
        "app.security.read-only.password=test-viewer-password",
        "app.security.operator.username=test-operator",
        "app.security.operator.password=test-operator-password",
        "app.security.admin.username=test-admin",
        "app.security.admin.password=test-admin-password"
})
@AutoConfigureWebTestClient
class SecurityAuditEndToEndTest {
    @Autowired
    private WebTestClient webClient;

    @Autowired
    private InMemoryIncidentRepository incidentRepository;

    @Test
    void enforcesRolesAndAuditsDeniedAndSuccessfulMutations() {
        String incidentId = "security-" + UUID.randomUUID();
        Instant now = Instant.now();
        incidentRepository.save(Incident.open(incidentId, "dedup-" + incidentId,
                "orders", "prod", "fingerprint", "ERROR", 5,
                now.minusSeconds(60), now,
                new AnomalyPolicyReference("global-default", 1))).block();

        webClient.get().uri("/api/v1/incidents/{id}", incidentId)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");

        webClient.get().uri("/api/v1/incidents/{id}", incidentId)
                .headers(headers -> headers.setBasicAuth("test-viewer", "test-viewer-password"))
                .exchange()
                .expectStatus().isOk();

        webClient.patch().uri("/api/v1/incidents/{id}/assignee", incidentId)
                .headers(headers -> headers.setBasicAuth("test-viewer", "test-viewer-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"assignee\":\"alice\"}")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACCESS_DENIED");

        webClient.patch().uri("/api/v1/incidents/{id}/assignee", incidentId)
                .headers(headers -> headers.setBasicAuth("test-operator", "test-operator-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"assignee\":\"alice\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.assignee").isEqualTo("alice");

        webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/audit-records")
                        .queryParam("targetType", "INCIDENT")
                        .queryParam("targetId", incidentId)
                        .build())
                .headers(headers -> headers.setBasicAuth("test-viewer", "test-viewer-password"))
                .exchange()
                .expectStatus().isForbidden();

        webClient.get().uri(uriBuilder -> uriBuilder.path("/api/v1/audit-records")
                        .queryParam("targetType", "INCIDENT")
                        .queryParam("targetId", incidentId)
                        .build())
                .headers(headers -> headers.setBasicAuth("test-admin", "test-admin-password"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2)
                .jsonPath("$.items[0].action").isEqualTo("INCIDENT_ASSIGN")
                .jsonPath("$.items[0].outcome").isEqualTo("SUCCEEDED")
                .jsonPath("$.items[0].actor").isEqualTo("test-operator")
                .jsonPath("$.items[0].beforeState.assignee").isEqualTo("unassigned")
                .jsonPath("$.items[0].afterState.assignee").isEqualTo("alice")
                .jsonPath("$.items[1].outcome").isEqualTo("DENIED")
                .jsonPath("$.items[1].actor").isEqualTo("test-viewer");
    }
}
