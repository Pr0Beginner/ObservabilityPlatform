package org.zmy.observabilityplatform.incident.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.incident.application.notification.IncidentNotifier;
import org.zmy.observabilityplatform.incident.domain.model.Incident;
import org.zmy.observabilityplatform.incident.domain.model.IncidentNotification;
import org.zmy.observabilityplatform.incident.domain.model.NotificationType;
import org.zmy.observabilityplatform.incident.domain.repository.IncidentNotificationRepository;
import org.zmy.observabilityplatform.incident.domain.repository.IncidentRepository;
import org.zmy.observabilityplatform.shared.exception.NotFoundException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class IncidentNotificationService {
    private final IncidentNotificationRepository repository;
    private final IncidentRepository incidentRepository;
    private final IncidentNotifier notifier;
    private final Clock clock;
    private final Duration leaseDuration;
    private final String workerId = UUID.randomUUID().toString();

    public IncidentNotificationService(IncidentNotificationRepository repository,
                                       IncidentRepository incidentRepository,
                                       IncidentNotifier notifier, Clock clock,
                                       @Value("${app.notification.lease-seconds:120}") long leaseSeconds) {
        if (leaseSeconds < 1) {
            throw new IllegalArgumentException("notification leaseSeconds must be positive");
        }
        this.repository = repository;
        this.incidentRepository = incidentRepository;
        this.notifier = notifier;
        this.clock = clock;
        this.leaseDuration = Duration.ofSeconds(leaseSeconds);
    }

    public Mono<Void> notify(Incident incident, NotificationType type) {
        return Mono.defer(() -> {
            String key = incident.getId() + "|" + type + "|" + incident.getVersion();
            IncidentNotification notification = IncidentNotification.pending(
                    UUID.randomUUID().toString(), key, incident.getId(), type, clock.instant());
            return repository.createIfAbsent(notification)
                    .filter(Boolean.TRUE::equals)
                    .flatMap(created -> claim(notification.getNotificationKey()))
                    .flatMap(claimed -> deliver(claimed, incident))
                    .then();
        });
    }

    public Mono<Void> retry(int limit) {
        return Mono.defer(() -> {
            Instant now = clock.instant();
            return repository.claimRetryable(workerId, now, now.plus(leaseDuration),
                            Math.max(1, Math.min(limit, 100)))
                    .concatMap(notification -> incidentRepository.findById(notification.getIncidentId())
                            .flatMap(incident -> deliver(notification, incident)))
                    .then();
        });
    }

    public Flux<IncidentNotification> findByIncidentId(String incidentId, int requestedLimit) {
        return incidentRepository.findById(incidentId)
                .switchIfEmpty(Mono.error(new NotFoundException("Incident not found: " + incidentId)))
                .flatMapMany(incident -> repository.findByIncidentId(
                        incidentId, Math.max(1, Math.min(requestedLimit, 100))));
    }

    private Mono<Void> deliver(IncidentNotification notification, Incident incident) {
        return notifier.send(incident, notification)
                .then(Mono.defer(() -> repository.complete(notification.sent(clock.instant()), workerId)))
                .onErrorResume(error -> repository.complete(
                        notification.failed(message(error), clock.instant()), workerId))
                .then();
    }

    private Mono<IncidentNotification> claim(String notificationKey) {
        Instant now = clock.instant();
        return repository.claim(notificationKey, workerId, now, now.plus(leaseDuration));
    }

    private String message(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
