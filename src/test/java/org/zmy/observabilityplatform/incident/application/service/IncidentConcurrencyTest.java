package org.zmy.observabilityplatform.incident.application.service;

import org.junit.jupiter.api.Test;
import org.zmy.observabilityplatform.incident.application.notification.IncidentNotifier;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicyReference;
import org.zmy.observabilityplatform.incident.domain.model.Incident;
import org.zmy.observabilityplatform.incident.domain.model.IncidentStatus;
import org.zmy.observabilityplatform.incident.domain.repository.IncidentRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.memory.InMemoryIncidentNotificationRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.memory.InMemoryIncidentRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentConcurrencyTest {
    private static final Instant START = Instant.parse("2026-09-19T10:00:00Z");
    private static final AnomalyPolicyReference POLICY = new AnomalyPolicyReference("global-default", 1);

    @Test
    void createsOnlyOneIncidentForTheSameDeduplicationKey() {
        InMemoryIncidentRepository repository = new InMemoryIncidentRepository();

        var results = Flux.range(0, 20)
                .flatMap(index -> Mono.defer(() -> repository.save(Incident.open(
                                UUID.randomUUID().toString(), "same-key", "orders", "prod", "fingerprint",
                                "ERROR", 3, START, START.plusSeconds(1), POLICY)))
                        .subscribeOn(Schedulers.parallel()))
                .collectList().block();

        assertThat(results).extracting(Incident::getId).containsOnly(results.get(0).getId());
        assertThat(repository.findAll().count().block()).isEqualTo(1);
    }

    @Test
    void replaysRecoveryAndReopenOperationsAfterConcurrentChanges() {
        InMemoryIncidentRepository storage = new InMemoryIncidentRepository();
        Incident opened = storage.save(Incident.open("incident-1", "dedup-1", "orders", "prod", "fp-1",
                "ERROR", 3, START, START.plusSeconds(1), POLICY)).block();
        Incident oneHealthyWindow = storage.save(opened.registerHealthyWindow(
                2, START.plusSeconds(60), START.plusSeconds(61), POLICY)).block();

        IncidentLifecycleService recoveryLifecycle = lifecycle(conflictOnce(storage,
                current -> current.assignTo("recovery-owner", START.plusSeconds(62))));
        Incident recovered = recoveryLifecycle.update(oneHealthyWindow.getId(), current ->
                current.registerHealthyWindow(
                        2, START.plusSeconds(120), START.plusSeconds(63), POLICY)).block();

        assertThat(recovered.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(recovered.getAssignee()).isEqualTo("recovery-owner");

        IncidentLifecycleService reopenLifecycle = lifecycle(conflictOnce(storage,
                current -> current.assignTo("reopen-owner", START.plusSeconds(64))));
        Incident reopened = reopenLifecycle.update(recovered.getId(), current -> current.registerOccurrences(
                5, START.plusSeconds(180), START.plusSeconds(65), POLICY)).block();

        assertThat(reopened.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(reopened.getAssignee()).isEqualTo("reopen-owner");
        assertThat(reopened.getVersion()).isGreaterThan(recovered.getVersion());
    }

    private IncidentLifecycleService lifecycle(IncidentRepository repository) {
        Clock clock = Clock.fixed(START.plusSeconds(300), ZoneOffset.UTC);
        IncidentNotifier notifier = (incident, notification) -> Mono.empty();
        IncidentNotificationService notifications = new IncidentNotificationService(
                new InMemoryIncidentNotificationRepository(), repository, notifier, clock, 120);
        return new IncidentLifecycleService(repository, notifications);
    }

    private IncidentRepository conflictOnce(InMemoryIncidentRepository delegate,
                                            UnaryOperator<Incident> concurrentOperation) {
        return new IncidentRepository() {
            private final AtomicBoolean conflictPending = new AtomicBoolean(true);

            @Override
            public Mono<Incident> save(Incident incident) {
                if (incident.getVersion() > 0 && conflictPending.compareAndSet(true, false)) {
                    return delegate.findById(incident.getId())
                            .flatMap(current -> delegate.save(concurrentOperation.apply(current)))
                            .then(Mono.defer(() -> delegate.save(incident)));
                }
                return delegate.save(incident);
            }

            @Override
            public Mono<Incident> findById(String id) {
                return delegate.findById(id);
            }

            @Override
            public Mono<Incident> findByDedupKey(String dedupKey) {
                return delegate.findByDedupKey(dedupKey);
            }

            @Override
            public Flux<Incident> findAll() {
                return delegate.findAll();
            }
        };
    }
}
