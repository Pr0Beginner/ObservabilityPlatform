package org.zmy.observabilityplatform.incident.infrastructure.repository.memory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.incident.domain.model.IncidentNotification;
import org.zmy.observabilityplatform.incident.domain.repository.IncidentNotificationRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "local", matchIfMissing = true)
public class InMemoryIncidentNotificationRepository implements IncidentNotificationRepository {
    private final Map<String, IncidentNotification> byKey = new ConcurrentHashMap<>();

    @Override
    public Mono<Boolean> createIfAbsent(IncidentNotification notification) {
        return Mono.just(byKey.putIfAbsent(notification.getNotificationKey(), notification) == null);
    }

    @Override
    public synchronized Mono<IncidentNotification> claim(String notificationKey, String owner,
                                                          Instant now, Instant leaseUntil) {
        IncidentNotification current = byKey.get(notificationKey);
        if (current == null || !current.canBeClaimedAt(now)) {
            return Mono.empty();
        }
        IncidentNotification claimed = current.claim(owner, leaseUntil, now);
        byKey.put(notificationKey, claimed);
        return Mono.just(claimed);
    }

    @Override
    public synchronized Flux<IncidentNotification> claimRetryable(String owner, Instant now,
                                                                   Instant leaseUntil, int limit) {
        List<IncidentNotification> claimed = new ArrayList<>();
        byKey.values().stream()
                .filter(value -> value.canBeClaimedAt(now))
                .sorted(Comparator.comparing(IncidentNotification::getUpdatedAt))
                .limit(limit)
                .forEach(value -> {
                    IncidentNotification leased = value.claim(owner, leaseUntil, now);
                    byKey.put(leased.getNotificationKey(), leased);
                    claimed.add(leased);
                });
        return Flux.fromIterable(claimed);
    }

    @Override
    public synchronized Mono<Boolean> complete(IncidentNotification notification, String owner) {
        IncidentNotification current = byKey.get(notification.getNotificationKey());
        if (current == null || !current.getId().equals(notification.getId())
                || current.getLeaseOwner() == null || !current.getLeaseOwner().equals(owner)) {
            return Mono.just(false);
        }
        byKey.put(notification.getNotificationKey(), notification);
        return Mono.just(true);
    }

    @Override
    public Flux<IncidentNotification> findByIncidentId(String incidentId, int limit) {
        return Flux.fromIterable(byKey.values())
                .filter(value -> value.getIncidentId().equals(incidentId))
                .sort((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .take(limit);
    }
}
