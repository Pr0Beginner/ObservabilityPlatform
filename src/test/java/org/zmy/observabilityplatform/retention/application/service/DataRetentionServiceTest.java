package org.zmy.observabilityplatform.retention.application.service;

import org.junit.jupiter.api.Test;
import org.zmy.observabilityplatform.audit.domain.model.AuditAction;
import org.zmy.observabilityplatform.audit.domain.model.AuditActor;
import org.zmy.observabilityplatform.audit.domain.model.AuditOutcome;
import org.zmy.observabilityplatform.audit.domain.model.AuditRecord;
import org.zmy.observabilityplatform.audit.domain.model.AuditTargetType;
import org.zmy.observabilityplatform.audit.infrastructure.repository.memory.InMemoryAuditRepository;
import org.zmy.observabilityplatform.incident.domain.model.IncidentNotification;
import org.zmy.observabilityplatform.incident.domain.model.MetricKey;
import org.zmy.observabilityplatform.incident.domain.model.NotificationType;
import org.zmy.observabilityplatform.incident.domain.repository.MetricTraceSampleRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.memory.InMemoryAnomalyPolicyRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.memory.InMemoryIncidentNotificationRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.memory.InMemoryMetricTraceSampleRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.memory.InMemoryMetricWindowRepository;
import org.zmy.observabilityplatform.retention.application.dto.RetentionCleanupResult;
import org.zmy.observabilityplatform.retention.infrastructure.configuration.DataRetentionProperties;
import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterMessage;
import org.zmy.observabilityplatform.shared.messaging.infrastructure.repository.memory.InMemoryDeadLetterRepository;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataRetentionServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");

    @Test
    void protectsPolicyBaselinesAndOnlyDeletesEligibleOperationalData() {
        InMemoryMetricWindowRepository metrics = new InMemoryMetricWindowRepository();
        InMemoryMetricTraceSampleRepository samples = new InMemoryMetricTraceSampleRepository();
        InMemoryIncidentNotificationRepository notifications = new InMemoryIncidentNotificationRepository();
        InMemoryDeadLetterRepository deadLetters = new InMemoryDeadLetterRepository();
        InMemoryAuditRepository audits = new InMemoryAuditRepository();
        DataRetentionProperties properties = properties();
        properties.setMetricWindowDays(1);
        DataRetentionService service = service(metrics, samples, notifications, deadLetters, audits);
        MetricKey key = MetricKey.requestTotal("orders", "prod", "GET /orders");
        Instant expiredMetric = NOW.minus(Duration.ofDays(3));
        Instant protectedBaseline = NOW.minus(Duration.ofHours(36));
        Instant recentMetric = NOW.minus(Duration.ofHours(1));
        recordMetric(metrics, samples, key, expiredMetric, "trace-expired");
        recordMetric(metrics, samples, key, protectedBaseline, "trace-baseline");
        recordMetric(metrics, samples, key, recentMetric, "trace-recent");

        Instant oldNotification = NOW.minus(Duration.ofDays(100));
        IncidentNotification sent = claimedNotification("sent", oldNotification).sent(oldNotification.plusSeconds(2));
        IncidentNotification failed = claimedNotification("failed", oldNotification)
                .deliveryFailed("temporary failure", 5, oldNotification.plusSeconds(2));
        notifications.createIfAbsent(sent).block();
        notifications.createIfAbsent(failed).block();

        DeadLetterMessage replayed = deadLetter(1, NOW.minus(Duration.ofDays(40)))
                .replayed(NOW.minus(Duration.ofDays(39)));
        DeadLetterMessage unresolved = deadLetter(2, NOW.minus(Duration.ofDays(200)));
        DeadLetterMessage recent = deadLetter(3, NOW.minus(Duration.ofDays(2)));
        deadLetters.saveIfAbsent(replayed).block();
        deadLetters.saveIfAbsent(unresolved).block();
        deadLetters.saveIfAbsent(recent).block();
        audits.save(audit("old-audit", NOW.minus(Duration.ofDays(400)))).block();
        audits.save(audit("recent-audit", NOW.minus(Duration.ofDays(2)))).block();

        Map<String, RetentionCleanupResult> results = service.cleanup(properties.toPolicy())
                .collectMap(RetentionCleanupResult::getTarget).block();

        assertThat(results.values()).allMatch(RetentionCleanupResult::isSuccessful);
        assertThat(results.get("metric_windows").getDeletedRecords()).isEqualTo(1);
        assertThat(results.get("metric_trace_samples").getDeletedRecords()).isEqualTo(1);
        assertThat(metrics.find(key, expiredMetric).block()).isNull();
        assertThat(samples.findTraceId(key, expiredMetric).block()).isNull();
        assertThat(metrics.find(key, protectedBaseline).block()).isNotNull();
        assertThat(samples.findTraceId(key, protectedBaseline).block()).isEqualTo("trace-baseline");
        assertThat(metrics.find(key, recentMetric).block()).isNotNull();
        assertThat(notifications.findByIncidentId("incident-1", 10).collectList().block())
                .extracting(IncidentNotification::getNotificationKey)
                .containsExactly("failed");
        assertThat(deadLetters.findById(replayed.getId()).block()).isNull();
        assertThat(deadLetters.findById(unresolved.getId()).block()).isNull();
        assertThat(deadLetters.findById(recent.getId()).block()).isNotNull();
        assertThat(audits.search(new org.zmy.observabilityplatform.audit.application.query.AuditSearchQuery(
                null, null, null, null, null, null, null, 0, 10)).block().getItems())
                .extracting(AuditRecord::getId)
                .containsExactly("recent-audit");
    }

    @Test
    void boundsTheNumberOfDeletedRowsPerRun() {
        InMemoryMetricWindowRepository metrics = new InMemoryMetricWindowRepository();
        DataRetentionProperties properties = properties();
        properties.setBatchSize(1);
        properties.setMaxBatchesPerRun(2);
        DataRetentionService service = service(metrics, new InMemoryMetricTraceSampleRepository(),
                new InMemoryIncidentNotificationRepository(), new InMemoryDeadLetterRepository());
        MetricKey key = MetricKey.requestTotal("orders", "prod", "GET /orders");
        List<Instant> expired = List.of(NOW.minus(Duration.ofDays(12)), NOW.minus(Duration.ofDays(11)),
                NOW.minus(Duration.ofDays(10)));
        expired.forEach(window -> metrics.increment(key, window, 1).block());

        RetentionCleanupResult result = service.cleanup(properties.toPolicy())
                .filter(value -> value.getTarget().equals("metric_windows")).single().block();
        long remaining = expired.stream().filter(window -> metrics.find(key, window).block() != null).count();

        assertThat(result.getDeletedRecords()).isEqualTo(2);
        assertThat(remaining).isEqualTo(1);
    }

    @Test
    void isolatesOneCleanupFailureAndContinuesWithOtherTargets() {
        InMemoryMetricWindowRepository metrics = new InMemoryMetricWindowRepository();
        MetricKey key = MetricKey.requestTotal("orders", "prod", "GET /orders");
        Instant expired = NOW.minus(Duration.ofDays(10));
        metrics.increment(key, expired, 1).block();
        MetricTraceSampleRepository failingSamples = mock(MetricTraceSampleRepository.class);
        when(failingSamples.deleteBefore(any(Instant.class), anyInt()))
                .thenReturn(Mono.error(new IllegalStateException("forced cleanup failure")));
        DataRetentionProperties properties = properties();
        DataRetentionService service = new DataRetentionService(metrics, failingSamples,
                new InMemoryIncidentNotificationRepository(), new InMemoryDeadLetterRepository(),
                new InMemoryAnomalyPolicyRepository(), new InMemoryAuditRepository(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        Map<String, RetentionCleanupResult> results = service.cleanup(properties.toPolicy())
                .collectMap(RetentionCleanupResult::getTarget).block();

        assertThat(results.get("metric_trace_samples").isSuccessful()).isFalse();
        assertThat(results.get("metric_windows").isSuccessful()).isTrue();
        assertThat(results.get("metric_windows").getDeletedRecords()).isEqualTo(1);
        assertThat(metrics.find(key, expired).block()).isNull();
    }

    private DataRetentionService service(InMemoryMetricWindowRepository metrics,
                                         InMemoryMetricTraceSampleRepository samples,
                                         InMemoryIncidentNotificationRepository notifications,
                                         InMemoryDeadLetterRepository deadLetters) {
        return new DataRetentionService(metrics, samples, notifications, deadLetters,
                new InMemoryAnomalyPolicyRepository(), new InMemoryAuditRepository(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private DataRetentionService service(InMemoryMetricWindowRepository metrics,
                                         InMemoryMetricTraceSampleRepository samples,
                                         InMemoryIncidentNotificationRepository notifications,
                                         InMemoryDeadLetterRepository deadLetters,
                                         InMemoryAuditRepository audits) {
        return new DataRetentionService(metrics, samples, notifications, deadLetters,
                new InMemoryAnomalyPolicyRepository(), audits, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private DataRetentionProperties properties() {
        DataRetentionProperties properties = new DataRetentionProperties();
        properties.setMetricWindowDays(7);
        properties.setMetricBaselineSafetyHours(24);
        properties.setNotificationDays(90);
        properties.setReplayedDeadLetterDays(30);
        properties.setUnresolvedDeadLetterDays(180);
        properties.setAuditDays(365);
        properties.setBatchSize(10);
        properties.setMaxBatchesPerRun(10);
        return properties;
    }

    private void recordMetric(InMemoryMetricWindowRepository metrics,
                              InMemoryMetricTraceSampleRepository samples,
                              MetricKey key, Instant window, String traceId) {
        metrics.increment(key, window, 1).block();
        samples.recordIfAbsent(key, window, traceId).block();
    }

    private IncidentNotification claimedNotification(String key, Instant createdAt) {
        return IncidentNotification.pending(key, key, "incident-1", NotificationType.OPENED, createdAt)
                .claim("worker", createdAt.plusSeconds(60), createdAt.plusSeconds(1));
    }

    private DeadLetterMessage deadLetter(long offset, Instant failedAt) {
        return DeadLetterMessage.captured("logs.raw.v1", "key-" + offset, "{}", "invalid", 0, offset, failedAt);
    }

    private AuditRecord audit(String id, Instant occurredAt) {
        return AuditRecord.create(id, new AuditActor("operator", List.of("OPERATOR")),
                AuditAction.INCIDENT_ASSIGN, AuditTargetType.INCIDENT, "incident-1",
                AuditOutcome.SUCCEEDED, "trace-1", occurredAt, Map.of(), Map.of(), null);
    }
}
