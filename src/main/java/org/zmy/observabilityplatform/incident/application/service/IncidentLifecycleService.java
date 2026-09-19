package org.zmy.observabilityplatform.incident.application.service;

import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.incident.domain.model.Incident;
import org.zmy.observabilityplatform.incident.domain.model.IncidentStatus;
import org.zmy.observabilityplatform.incident.domain.model.NotificationType;
import org.zmy.observabilityplatform.incident.domain.exception.IncidentVersionConflictException;
import org.zmy.observabilityplatform.incident.domain.repository.IncidentRepository;
import org.zmy.observabilityplatform.shared.exception.NotFoundException;
import reactor.core.publisher.Mono;

import java.util.function.UnaryOperator;

@Service
public class IncidentLifecycleService {
    private static final int MAX_UPDATE_ATTEMPTS = 5;
    private final IncidentRepository repository;
    private final IncidentNotificationService notificationService;

    public IncidentLifecycleService(IncidentRepository repository,
                                    IncidentNotificationService notificationService) {
        this.repository = repository;
        this.notificationService = notificationService;
    }

    public Mono<Incident> open(Incident incident) {
        return Mono.defer(() -> repository.save(incident))
                .flatMap(saved -> saved.getId().equals(incident.getId())
                        ? notificationService.notify(saved, NotificationType.OPENED).thenReturn(saved)
                        : Mono.just(saved));
    }

    public Mono<Incident> update(String incidentId, UnaryOperator<Incident> operation) {
        return Mono.defer(() -> update(incidentId, operation, 1));
    }

    private Mono<Incident> update(String incidentId, UnaryOperator<Incident> operation, int attempt) {
        return repository.findById(incidentId)
                .switchIfEmpty(Mono.error(new NotFoundException("Incident not found: " + incidentId)))
                .flatMap(before -> {
                    Incident after = operation.apply(before);
                    if (before.equals(after)) {
                        return Mono.just(before);
                    }
                    return repository.save(after).flatMap(saved -> notifyTransition(before, saved));
                })
                .onErrorResume(IncidentVersionConflictException.class, error -> attempt < MAX_UPDATE_ATTEMPTS
                        ? update(incidentId, operation, attempt + 1)
                        : Mono.error(error));
    }

    private Mono<Incident> notifyTransition(Incident before, Incident saved) {
        if (isTerminal(before.getStatus()) && saved.getStatus() == IncidentStatus.OPEN) {
            return notificationService.notify(saved, NotificationType.OPENED).thenReturn(saved);
        }
        if (!isTerminal(before.getStatus()) && isTerminal(saved.getStatus())) {
            return notificationService.notify(saved, NotificationType.RECOVERED).thenReturn(saved);
        }
        return Mono.just(saved);
    }

    private boolean isTerminal(IncidentStatus status) {
        return status == IncidentStatus.RESOLVED || status == IncidentStatus.CLOSED;
    }
}
