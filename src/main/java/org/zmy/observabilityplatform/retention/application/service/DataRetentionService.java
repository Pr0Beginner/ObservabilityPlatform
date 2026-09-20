package org.zmy.observabilityplatform.retention.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.audit.domain.repository.AuditRepository;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicy;
import org.zmy.observabilityplatform.incident.domain.repository.AnomalyPolicyRepository;
import org.zmy.observabilityplatform.incident.domain.repository.IncidentNotificationRepository;
import org.zmy.observabilityplatform.incident.domain.repository.MetricTraceSampleRepository;
import org.zmy.observabilityplatform.incident.domain.repository.MetricWindowRepository;
import org.zmy.observabilityplatform.retention.application.dto.RetentionCleanupResult;
import org.zmy.observabilityplatform.retention.application.model.DataRetentionPolicy;
import org.zmy.observabilityplatform.shared.messaging.domain.repository.DeadLetterRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Slf4j
@Service
public class DataRetentionService {
    private final MetricWindowRepository metricWindowRepository;
    private final MetricTraceSampleRepository traceSampleRepository;
    private final IncidentNotificationRepository notificationRepository;
    private final DeadLetterRepository deadLetterRepository;
    private final AnomalyPolicyRepository anomalyPolicyRepository;
    private final AuditRepository auditRepository;
    private final Clock clock;

    public DataRetentionService(MetricWindowRepository metricWindowRepository,
                                MetricTraceSampleRepository traceSampleRepository,
                                IncidentNotificationRepository notificationRepository,
                                DeadLetterRepository deadLetterRepository,
                                AnomalyPolicyRepository anomalyPolicyRepository,
                                AuditRepository auditRepository,
                                Clock clock) {
        this.metricWindowRepository = metricWindowRepository;
        this.traceSampleRepository = traceSampleRepository;
        this.notificationRepository = notificationRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.anomalyPolicyRepository = anomalyPolicyRepository;
        this.auditRepository = auditRepository;
        this.clock = clock;
    }

    public Flux<RetentionCleanupResult> cleanup(DataRetentionPolicy policy) {
        if (policy == null) {
            return Flux.error(new IllegalArgumentException("retention policy must not be null"));
        }
        Instant now = clock.instant();
        Flux<RetentionCleanupResult> metricCleanup = effectiveMetricRetention(policy)
                .flatMapMany(retention -> {
                    Instant cutoff = now.minus(retention);
                    return Flux.concat(
                            clean("metric_trace_samples", () -> traceSampleRepository.deleteBefore(
                                    cutoff, policy.getBatchSize()), policy),
                            clean("metric_windows", () -> metricWindowRepository.deleteBefore(
                                    cutoff, policy.getBatchSize()), policy));
                })
                .onErrorResume(error -> {
                    log.error("Metric retention skipped because its policy guard could not be evaluated", error);
                    return Flux.just(RetentionCleanupResult.failed("metric_retention_guard", 0,
                            now, Duration.ZERO, message(error)));
                });

        return Flux.concat(
                metricCleanup,
                clean("incident_notifications", () -> notificationRepository.deleteTerminalBefore(
                        now.minus(policy.getNotificationRetention()), policy.getBatchSize()), policy),
                clean("replayed_dead_letters", () -> deadLetterRepository.deleteReplayedBefore(
                        now.minus(policy.getReplayedDeadLetterRetention()), policy.getBatchSize()), policy),
                clean("unresolved_dead_letters", () -> deadLetterRepository.deleteUnreplayedBefore(
                        now.minus(policy.getUnresolvedDeadLetterRetention()), now,
                        policy.getBatchSize()), policy),
                clean("audit_records", () -> auditRepository.deleteBefore(
                        now.minus(policy.getAuditRetention()), policy.getBatchSize()), policy));
    }

    private Mono<Duration> effectiveMetricRetention(DataRetentionPolicy policy) {
        Duration configured = policy.getMetricWindowRetention();
        return anomalyPolicyRepository.findAll()
                .filter(AnomalyPolicy::isEnabled)
                .map(anomalyPolicy -> anomalyPolicy.getSettings().getComparisonPeriodMinutes())
                .reduce(0, Math::max)
                .map(longestPeriod -> {
                    Duration required = Duration.ofMinutes(longestPeriod)
                            .plus(policy.getMetricBaselineSafety());
                    return configured.compareTo(required) >= 0 ? configured : required;
                });
    }

    private Mono<RetentionCleanupResult> clean(String target, Supplier<Mono<Long>> deleteBatch,
                                               DataRetentionPolicy policy) {
        return Mono.defer(() -> {
            Instant startedAt = clock.instant();
            long startedNanos = System.nanoTime();
            AtomicLong total = new AtomicLong();
            return Flux.range(0, policy.getMaxBatchesPerRun())
                    .concatMap(index -> Mono.defer(deleteBatch))
                    .doOnNext(total::addAndGet)
                    .takeUntil(deleted -> deleted < policy.getBatchSize())
                    .then(Mono.fromSupplier(() -> RetentionCleanupResult.succeeded(
                            target, total.get(), startedAt, elapsedSince(startedNanos))))
                    .onErrorResume(error -> {
                        log.error("Retention cleanup failed: target={}", target, error);
                        return Mono.just(RetentionCleanupResult.failed(target, total.get(), startedAt,
                                elapsedSince(startedNanos), message(error)));
                    });
        });
    }

    private Duration elapsedSince(long startedNanos) {
        return Duration.ofNanos(Math.max(0, System.nanoTime() - startedNanos));
    }

    private String message(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
