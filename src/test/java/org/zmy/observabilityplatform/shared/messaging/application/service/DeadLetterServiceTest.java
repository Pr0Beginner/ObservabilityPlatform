package org.zmy.observabilityplatform.shared.messaging.application.service;

import org.junit.jupiter.api.Test;
import org.zmy.observabilityplatform.shared.exception.BusinessConflictException;
import org.zmy.observabilityplatform.shared.messaging.application.query.DeadLetterSearchQuery;
import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterMessage;
import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterStatus;
import org.zmy.observabilityplatform.shared.messaging.domain.model.ReplayAttemptStatus;
import org.zmy.observabilityplatform.shared.messaging.infrastructure.repository.memory.InMemoryDeadLetterReplayAttemptRepository;
import org.zmy.observabilityplatform.shared.messaging.infrastructure.repository.memory.InMemoryDeadLetterRepository;
import org.zmy.observabilityplatform.audit.application.query.AuditSearchQuery;
import org.zmy.observabilityplatform.audit.application.service.AuditTrailService;
import org.zmy.observabilityplatform.audit.domain.model.AuditAction;
import org.zmy.observabilityplatform.audit.domain.model.AuditActor;
import org.zmy.observabilityplatform.audit.domain.model.AuditOutcome;
import org.zmy.observabilityplatform.audit.domain.model.AuditTargetType;
import org.zmy.observabilityplatform.audit.infrastructure.repository.memory.InMemoryAuditRepository;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.zmy.observabilityplatform.support.AuditTestFixture.auditTrail;

class DeadLetterServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");

    @Test
    void filtersStructuredFailuresAndPaginatesByStatus() {
        InMemoryDeadLetterRepository messages = new InMemoryDeadLetterRepository();
        InMemoryDeadLetterReplayAttemptRepository attempts = new InMemoryDeadLetterReplayAttemptRepository();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        DeadLetterService service = new DeadLetterService(messages, messages, attempts,
                message -> Mono.empty(), clock, auditTrail(clock), 120);
        DeadLetterMessage unresolved = message(1, "java.lang.IllegalArgumentException", NOW.minusSeconds(30));
        DeadLetterMessage replayed = message(2, "java.lang.IllegalArgumentException", NOW.minusSeconds(60))
                .replayed(NOW.minusSeconds(10));
        messages.saveIfAbsent(unresolved).block();
        messages.saveIfAbsent(replayed).block();

        var result = service.search(new DeadLetterSearchQuery("logs.raw.v1", DeadLetterStatus.UNRESOLVED,
                "java.lang.IllegalArgumentException", NOW.minusSeconds(120), NOW, 0, 10)).block();

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getItems()).containsExactly(unresolved);
    }

    @Test
    void recordsBothSuccessfulAndFailedReplayAttempts() {
        InMemoryDeadLetterRepository messages = new InMemoryDeadLetterRepository();
        InMemoryDeadLetterReplayAttemptRepository attempts = new InMemoryDeadLetterReplayAttemptRepository();
        InMemoryAuditRepository audits = new InMemoryAuditRepository();
        DeadLetterMessage successfulMessage = message(10, "TemporaryFailure", NOW.minusSeconds(30));
        messages.saveIfAbsent(successfulMessage).block();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        AuditTrailService auditTrail = new AuditTrailService(audits,
                () -> Mono.just(new AuditActor("test-operator", List.of("OPERATOR"))), clock);
        DeadLetterService successfulService = new DeadLetterService(messages, messages, attempts,
                message -> Mono.empty(), clock, auditTrail, 120);

        DeadLetterMessage replayed = successfulService.replay(successfulMessage.getId()).block();

        assertThat(replayed).isNotNull();
        assertThat(successfulService.findReplayAttempts(successfulMessage.getId(), 10).single().block().getStatus())
                .isEqualTo(ReplayAttemptStatus.SUCCEEDED);
        StepVerifier.create(successfulService.replay(successfulMessage.getId()))
                .expectError(BusinessConflictException.class)
                .verify();
        var replayAudits = audits.search(new AuditSearchQuery("test-operator",
                AuditAction.DEAD_LETTER_REPLAY, AuditTargetType.DEAD_LETTER,
                successfulMessage.getId(), null, null, null, 0, 10)).block();
        assertThat(replayAudits).isNotNull();
        assertThat(replayAudits.getItems()).extracting(record -> record.getOutcome())
                .containsExactlyInAnyOrder(AuditOutcome.SUCCEEDED, AuditOutcome.FAILED);

        DeadLetterMessage failedMessage = message(11, "PermanentFailure", NOW.minusSeconds(20));
        messages.saveIfAbsent(failedMessage).block();
        DeadLetterService failedService = new DeadLetterService(messages, messages, attempts,
                message -> Mono.error(new BusinessConflictException("replay rejected")),
                clock, auditTrail, 120);

        StepVerifier.create(failedService.replay(failedMessage.getId()))
                .expectError(BusinessConflictException.class)
                .verify();
        var failedAttempt = failedService.findReplayAttempts(failedMessage.getId(), 10).single().block();
        assertThat(failedAttempt).isNotNull();
        assertThat(failedAttempt.getStatus()).isEqualTo(ReplayAttemptStatus.FAILED);
        assertThat(failedAttempt.getFailureReason()).isEqualTo("replay rejected");
    }

    @Test
    void allowsOnlyOneReplayLeaseAndSupportsTakeoverAfterExpiry() {
        InMemoryDeadLetterRepository messages = new InMemoryDeadLetterRepository();
        DeadLetterMessage message = message(20, "TemporaryFailure", NOW.minusSeconds(30));
        messages.saveIfAbsent(message).block();

        assertThat(messages.claimForReplay(message.getId(), "worker-1", NOW, NOW.plusSeconds(30)).block())
                .isEqualTo(message);
        assertThat(messages.deleteUnreplayedBefore(NOW, NOW.plusSeconds(10), 10).block()).isZero();
        assertThat(messages.claimForReplay(message.getId(), "worker-2", NOW.plusSeconds(10),
                NOW.plusSeconds(40)).block()).isNull();
        assertThat(messages.claimForReplay(message.getId(), "worker-2", NOW.plusSeconds(31),
                NOW.plusSeconds(61)).block()).isEqualTo(message);
        assertThat(messages.completeReplay(message.getId(), "worker-1", NOW.plusSeconds(32)).block()).isNull();
        assertThat(messages.completeReplay(message.getId(), "worker-2", NOW.plusSeconds(32)).block()
                .getStatus()).isEqualTo(DeadLetterStatus.REPLAYED);
    }

    private DeadLetterMessage message(long offset, String failureType, Instant failedAt) {
        return DeadLetterMessage.captured("logs.raw.v1", "key-" + offset, "{}", failureType,
                "processing failed", 0, offset, failedAt);
    }
}
