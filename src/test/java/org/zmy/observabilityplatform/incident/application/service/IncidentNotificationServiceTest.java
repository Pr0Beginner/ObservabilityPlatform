package org.zmy.observabilityplatform.incident.application.service;

import org.junit.jupiter.api.Test;
import org.zmy.observabilityplatform.incident.application.notification.IncidentNotifier;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicyReference;
import org.zmy.observabilityplatform.incident.domain.model.Incident;
import org.zmy.observabilityplatform.incident.domain.model.IncidentNotification;
import org.zmy.observabilityplatform.incident.domain.model.NotificationStatus;
import org.zmy.observabilityplatform.incident.domain.model.NotificationType;
import org.zmy.observabilityplatform.incident.infrastructure.repository.memory.InMemoryIncidentNotificationRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.memory.InMemoryIncidentRepository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentNotificationServiceTest {
    @Test
    void sendsTheSameLifecycleNotificationOnlyOnce() {
        Instant now = Instant.parse("2026-09-19T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        InMemoryIncidentRepository incidents = new InMemoryIncidentRepository();
        Incident incident = incidents.save(Incident.open("incident-1", "dedup-1", "orders", "prod", "fp-1",
                "ERROR", 3, now.minusSeconds(60), now,
                new AnomalyPolicyReference("global-default", 1))).block();
        AtomicInteger deliveries = new AtomicInteger();
        IncidentNotifier notifier = (value, type) -> Mono.fromRunnable(deliveries::incrementAndGet);
        IncidentNotificationService service = new IncidentNotificationService(
                new InMemoryIncidentNotificationRepository(), incidents, notifier, clock, 120, 5);

        service.notify(incident, NotificationType.OPENED).block();
        service.notify(incident, NotificationType.OPENED).block();

        assertThat(deliveries).hasValue(1);
    }

    @Test
    void onlyOneInstanceSendsTheSameRetryableNotification() {
        Instant now = Instant.parse("2026-09-19T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        InMemoryIncidentRepository incidents = new InMemoryIncidentRepository();
        Incident incident = incidents.save(Incident.open("incident-1", "dedup-1", "orders", "prod", "fp-1",
                "ERROR", 3, now.minusSeconds(60), now,
                new AnomalyPolicyReference("global-default", 1))).block();
        InMemoryIncidentNotificationRepository notifications = new InMemoryIncidentNotificationRepository();
        notifications.createIfAbsent(IncidentNotification.pending(
                "notification-1", "notification-key", incident.getId(), NotificationType.OPENED, now)).block();
        AtomicInteger deliveries = new AtomicInteger();
        IncidentNotifier notifier = (value, notification) -> Mono.fromRunnable(deliveries::incrementAndGet);
        IncidentNotificationService first = new IncidentNotificationService(
                notifications, incidents, notifier, clock, 120, 5);
        IncidentNotificationService second = new IncidentNotificationService(
                notifications, incidents, notifier, clock, 120, 5);

        Mono.when(
                Mono.defer(() -> first.retry(10)).subscribeOn(Schedulers.parallel()),
                Mono.defer(() -> second.retry(10)).subscribeOn(Schedulers.parallel()))
                .block();

        assertThat(deliveries).hasValue(1);
        assertThat(notifications.findByIncidentId(incident.getId(), 10).single().block().getAttempts())
                .isEqualTo(1);
    }

    @Test
    void anotherInstanceCanTakeOverAnExpiredLease() {
        Instant now = Instant.parse("2026-09-19T12:00:00Z");
        InMemoryIncidentNotificationRepository repository = new InMemoryIncidentNotificationRepository();
        IncidentNotification pending = IncidentNotification.pending(
                "notification-1", "notification-key", "incident-1", NotificationType.OPENED, now);
        repository.createIfAbsent(pending).block();

        IncidentNotification firstClaim = repository.claim(
                pending.getNotificationKey(), "worker-1", now, now.plusSeconds(60)).block();
        IncidentNotification earlyClaim = repository.claim(
                pending.getNotificationKey(), "worker-2", now.plusSeconds(30), now.plusSeconds(90)).block();
        IncidentNotification takeover = repository.claim(
                pending.getNotificationKey(), "worker-2", now.plusSeconds(61), now.plusSeconds(121)).block();

        assertThat(firstClaim.getLeaseOwner()).isEqualTo("worker-1");
        assertThat(earlyClaim).isNull();
        assertThat(takeover.getLeaseOwner()).isEqualTo("worker-2");
        assertThat(takeover.getAttempts()).isEqualTo(2);
        assertThat(repository.complete(firstClaim.sent(now.plusSeconds(62)), "worker-1").block()).isFalse();
        assertThat(repository.complete(takeover.sent(now.plusSeconds(63)), "worker-2").block()).isTrue();
    }

    @Test
    void stopsRetryingAfterTheConfiguredMaximumAttempts() {
        Instant now = Instant.parse("2026-09-19T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        InMemoryIncidentRepository incidents = new InMemoryIncidentRepository();
        Incident incident = incidents.save(Incident.open("incident-1", "dedup-1", "orders", "prod", "fp-1",
                "ERROR", 3, now.minusSeconds(60), now,
                new AnomalyPolicyReference("global-default", 1))).block();
        InMemoryIncidentNotificationRepository notifications = new InMemoryIncidentNotificationRepository();
        AtomicInteger deliveries = new AtomicInteger();
        IncidentNotifier notifier = (value, type) -> Mono.defer(() -> {
            deliveries.incrementAndGet();
            return Mono.error(new IllegalStateException("webhook unavailable"));
        });
        IncidentNotificationService service = new IncidentNotificationService(
                notifications, incidents, notifier, clock, 120, 2);

        service.notify(incident, NotificationType.OPENED).block();
        service.retry(10).block();
        service.retry(10).block();

        IncidentNotification notification = notifications.findByIncidentId(incident.getId(), 10).single().block();
        assertThat(deliveries).hasValue(2);
        assertThat(notification.getAttempts()).isEqualTo(2);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.EXHAUSTED);
    }
}
