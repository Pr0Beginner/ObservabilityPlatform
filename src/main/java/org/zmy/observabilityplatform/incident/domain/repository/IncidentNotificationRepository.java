package org.zmy.observabilityplatform.incident.domain.repository;

import org.zmy.observabilityplatform.incident.domain.model.IncidentNotification;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface IncidentNotificationRepository {
    Mono<Boolean> createIfAbsent(IncidentNotification notification);

    Mono<IncidentNotification> claim(String notificationKey, String owner, Instant now, Instant leaseUntil);

    Flux<IncidentNotification> claimRetryable(String owner, Instant now, Instant leaseUntil, int limit);

    Mono<Boolean> complete(IncidentNotification notification, String owner);

    Flux<IncidentNotification> findByIncidentId(String incidentId, int limit);
}
