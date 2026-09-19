package org.zmy.observabilityplatform.incident.infrastructure.repository.memory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.incident.domain.model.IncidentTraceLink;
import org.zmy.observabilityplatform.incident.domain.repository.IncidentTraceLinkRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "local", matchIfMissing = true)
public class InMemoryIncidentTraceLinkRepository implements IncidentTraceLinkRepository {
    private final Map<String, IncidentTraceLink> links = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> link(IncidentTraceLink link) {
        links.putIfAbsent(link.getIncidentId() + "|" + link.getTraceId(), link);
        return Mono.empty();
    }

    @Override
    public Flux<String> findTraceIdsByIncidentId(String incidentId, int limit) {
        return Flux.fromIterable(links.values())
                .filter(link -> link.getIncidentId().equals(incidentId))
                .sort((left, right) -> right.getLinkedAt().compareTo(left.getLinkedAt()))
                .map(IncidentTraceLink::getTraceId).take(limit);
    }

    @Override
    public Flux<String> findIncidentIdsByTraceId(String traceId, int limit) {
        return Flux.fromIterable(links.values())
                .filter(link -> link.getTraceId().equals(traceId))
                .sort((left, right) -> right.getLinkedAt().compareTo(left.getLinkedAt()))
                .map(IncidentTraceLink::getIncidentId).take(limit);
    }
}
