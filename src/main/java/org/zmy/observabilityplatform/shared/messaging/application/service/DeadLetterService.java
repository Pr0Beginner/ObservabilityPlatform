package org.zmy.observabilityplatform.shared.messaging.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.audit.application.service.AuditOperation;
import org.zmy.observabilityplatform.audit.application.service.AuditResult;
import org.zmy.observabilityplatform.audit.application.service.AuditState;
import org.zmy.observabilityplatform.audit.application.service.AuditTrailService;
import org.zmy.observabilityplatform.audit.domain.model.AuditAction;
import org.zmy.observabilityplatform.audit.domain.model.AuditTargetType;
import org.zmy.observabilityplatform.shared.application.query.PageResult;
import org.zmy.observabilityplatform.shared.exception.BusinessConflictException;
import org.zmy.observabilityplatform.shared.exception.NotFoundException;
import org.zmy.observabilityplatform.shared.messaging.application.publisher.DeadLetterReplayPublisher;
import org.zmy.observabilityplatform.shared.messaging.application.query.DeadLetterQueryRepository;
import org.zmy.observabilityplatform.shared.messaging.application.query.DeadLetterSearchQuery;
import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterMessage;
import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterReplayAttempt;
import org.zmy.observabilityplatform.shared.messaging.domain.repository.DeadLetterRepository;
import org.zmy.observabilityplatform.shared.messaging.domain.repository.DeadLetterReplayAttemptRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class DeadLetterService {
    private final DeadLetterRepository repository;
    private final DeadLetterQueryRepository queryRepository;
    private final DeadLetterReplayAttemptRepository replayAttemptRepository;
    private final DeadLetterReplayPublisher publisher;
    private final Clock clock;
    private final Duration replayLeaseDuration;
    private final AuditTrailService auditTrailService;

    public DeadLetterService(DeadLetterRepository repository, DeadLetterQueryRepository queryRepository,
                             DeadLetterReplayAttemptRepository replayAttemptRepository,
                             DeadLetterReplayPublisher publisher, Clock clock,
                             AuditTrailService auditTrailService,
                             @Value("${app.dead-letter.replay-lease-seconds:120}") long replayLeaseSeconds) {
        if (replayLeaseSeconds < 1) {
            throw new IllegalArgumentException("replayLeaseSeconds must be positive");
        }
        this.repository = repository;
        this.queryRepository = queryRepository;
        this.replayAttemptRepository = replayAttemptRepository;
        this.publisher = publisher;
        this.clock = clock;
        this.auditTrailService = auditTrailService;
        this.replayLeaseDuration = Duration.ofSeconds(replayLeaseSeconds);
    }

    public Mono<PageResult<DeadLetterMessage>> search(DeadLetterSearchQuery query) {
        return queryRepository.search(query);
    }

    public Mono<DeadLetterMessage> replay(String id) {
        AuditOperation operation = new AuditOperation(AuditAction.DEAD_LETTER_REPLAY,
                AuditTargetType.DEAD_LETTER, id);
        return auditTrailService.audit(operation, () -> replayInternal(id),
                        change -> AuditResult.changed(id, snapshot(change.before), snapshot(change.after)))
                .map(change -> change.after);
    }

    private Mono<ReplayChange> replayInternal(String id) {
        return Mono.defer(() -> {
            String leaseOwner = UUID.randomUUID().toString();
            return claimForReplay(id, leaseOwner).flatMap(message -> {
                DeadLetterReplayAttempt attempt = DeadLetterReplayAttempt.start(
                        UUID.randomUUID().toString(), id, clock.instant());
                return replayAttemptRepository.save(attempt)
                        .onErrorResume(error -> repository.releaseReplay(id, leaseOwner)
                                .then(Mono.error(error)))
                        .flatMap(saved -> publisher.publish(message)
                                .then(Mono.defer(() -> repository.completeReplay(id, leaseOwner, clock.instant())
                                        .switchIfEmpty(Mono.error(new BusinessConflictException(
                                                "Dead-letter replay lease is no longer owned: " + id)))))
                                .flatMap(replayed -> replayAttemptRepository.save(saved.succeed(clock.instant()))
                                        .thenReturn(new ReplayChange(message, replayed)))
                                .onErrorResume(error -> recordFailure(saved, error)
                                        .then(repository.releaseReplay(id, leaseOwner))
                                        .then(Mono.error(error))));
            });
        });
    }

    public Flux<DeadLetterReplayAttempt> findReplayAttempts(String id, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        return findMessage(id).flatMapMany(message -> replayAttemptRepository.findByDeadLetterId(id, limit));
    }

    private Mono<DeadLetterMessage> findMessage(String id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Dead-letter message not found: " + id)));
    }

    private Mono<DeadLetterMessage> claimForReplay(String id, String leaseOwner) {
        Instant now = clock.instant();
        return repository.claimForReplay(id, leaseOwner, now, now.plus(replayLeaseDuration))
                .switchIfEmpty(Mono.defer(() -> repository.findById(id)
                        .flatMap(message -> Mono.<DeadLetterMessage>error(new BusinessConflictException(
                                message.getReplayedAt() == null
                                        ? "Dead-letter message is already being replayed: " + id
                                        : "Dead-letter message has already been replayed: " + id)))
                        .switchIfEmpty(Mono.error(new NotFoundException(
                                "Dead-letter message not found: " + id)))));
    }

    private Mono<Void> recordFailure(DeadLetterReplayAttempt attempt, Throwable error) {
        String reason = error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getName() : error.getMessage();
        return replayAttemptRepository.save(attempt.fail(reason, clock.instant()))
                .then()
                .onErrorResume(recordingError -> {
                    error.addSuppressed(recordingError);
                    return Mono.empty();
                });
    }

    private java.util.Map<String, Object> snapshot(DeadLetterMessage message) {
        return AuditState.of("topic", message.getOriginalTopic(),
                "partition", message.getSourcePartition(),
                "offset", message.getSourceOffset(),
                "failureType", message.getFailureType(),
                "status", message.getStatus().name(),
                "replayedAt", message.getReplayedAt() == null ? null : message.getReplayedAt().toString());
    }

    private static final class ReplayChange {
        private final DeadLetterMessage before;
        private final DeadLetterMessage after;

        private ReplayChange(DeadLetterMessage before, DeadLetterMessage after) {
            this.before = before;
            this.after = after;
        }
    }
}
