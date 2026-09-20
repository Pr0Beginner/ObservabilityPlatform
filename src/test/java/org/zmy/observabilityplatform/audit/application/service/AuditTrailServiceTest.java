package org.zmy.observabilityplatform.audit.application.service;

import org.junit.jupiter.api.Test;
import org.zmy.observabilityplatform.audit.application.query.AuditSearchQuery;
import org.zmy.observabilityplatform.audit.domain.model.AuditAction;
import org.zmy.observabilityplatform.audit.domain.model.AuditActor;
import org.zmy.observabilityplatform.audit.domain.model.AuditOutcome;
import org.zmy.observabilityplatform.audit.domain.model.AuditTargetType;
import org.zmy.observabilityplatform.audit.infrastructure.repository.memory.InMemoryAuditRepository;
import org.zmy.observabilityplatform.audit.domain.model.AuditRecord;
import org.zmy.observabilityplatform.audit.domain.repository.AuditRepository;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditTrailServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");

    @Test
    void recordsSuccessAndFailureWithoutReplacingTheBusinessError() {
        InMemoryAuditRepository repository = new InMemoryAuditRepository();
        AuditTrailService service = new AuditTrailService(repository,
                () -> Mono.just(new AuditActor("alice", List.of("OPERATOR"))),
                Clock.fixed(NOW, ZoneOffset.UTC));
        AuditOperation operation = new AuditOperation(AuditAction.INCIDENT_ASSIGN,
                AuditTargetType.INCIDENT, "incident-1");

        String result = service.audit(operation, () -> Mono.just("done"),
                value -> AuditResult.changed("incident-1", Map.of("assignee", "unassigned"),
                        Map.of("assignee", "alice"))).block();
        assertThat(result).isEqualTo("done");

        IllegalStateException businessError = new IllegalStateException("cannot update");
        StepVerifier.create(service.audit(operation, () -> Mono.error(businessError),
                        value -> AuditResult.created("incident-1", Map.of())))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(businessError))
                .verify();

        var page = repository.search(new AuditSearchQuery("alice", AuditAction.INCIDENT_ASSIGN,
                AuditTargetType.INCIDENT, "incident-1", null, null, null, 0, 10)).block();
        assertThat(page).isNotNull();
        assertThat(page.getItems()).extracting(record -> record.getOutcome())
                .containsExactlyInAnyOrder(AuditOutcome.SUCCEEDED, AuditOutcome.FAILED);
    }

    @Test
    void auditStorageFailureDoesNotTurnACompletedMutationIntoAClientFailure() {
        AuditTrailService service = new AuditTrailService(
                new AuditRepository() {
                    @Override
                    public Mono<AuditRecord> save(AuditRecord record) {
                        return Mono.error(new IllegalStateException("audit storage unavailable"));
                    }

                    @Override
                    public Mono<Long> deleteBefore(Instant cutoff, int limit) {
                        return Mono.just(0L);
                    }
                },
                () -> Mono.just(new AuditActor("alice", List.of("OPERATOR"))),
                Clock.fixed(NOW, ZoneOffset.UTC));

        String result = service.audit(new AuditOperation(AuditAction.INCIDENT_ASSIGN,
                        AuditTargetType.INCIDENT, "incident-1"),
                () -> Mono.just("business result"),
                value -> AuditResult.created("incident-1", Map.of("assignee", "alice"))).block();

        assertThat(result).isEqualTo("business result");
    }
}
