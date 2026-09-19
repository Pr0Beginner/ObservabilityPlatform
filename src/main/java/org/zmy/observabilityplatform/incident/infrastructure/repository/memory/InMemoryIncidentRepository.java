package org.zmy.observabilityplatform.incident.infrastructure.repository.memory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.incident.domain.exception.IncidentVersionConflictException;
import org.zmy.observabilityplatform.incident.domain.model.Incident;
import org.zmy.observabilityplatform.incident.domain.repository.IncidentRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "local", matchIfMissing = true)
public class InMemoryIncidentRepository implements IncidentRepository {
    private final Map<String, Incident> incidents = new ConcurrentHashMap<>();

    @Override
    public synchronized Mono<Incident> save(Incident incident) {
        if (incident.getVersion() == 0) {
            Incident existing = incidents.values().stream()
                    .filter(value -> value.getDedupKey().equals(incident.getDedupKey()))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                return Mono.just(existing);
            }
            Incident persisted = incident.persistedAtVersion(1);
            incidents.put(persisted.getId(), persisted);
            return Mono.just(persisted);
        }
        Incident current = incidents.get(incident.getId());
        if (current == null || current.getVersion() != incident.getVersion()) {
            return Mono.error(new IncidentVersionConflictException(incident.getId(), incident.getVersion()));
        }
        Incident persisted = incident.persistedAtVersion(incident.getVersion() + 1);
        incidents.put(persisted.getId(), persisted);
        return Mono.just(persisted);
    }

    @Override
    public Mono<Incident> findById(String id) {
        return Mono.justOrEmpty(incidents.get(id));
    }

    @Override
    public Mono<Incident> findByDedupKey(String dedupKey) {
        return Flux.fromIterable(incidents.values())
                .filter(incident -> incident.getDedupKey().equals(dedupKey))
                .next();
    }

    @Override
    public Flux<Incident> findAll() {
        return Flux.fromStream(incidents.values().stream()
                .sorted(Comparator.comparing(Incident::getStartedAt).reversed()));
    }
}
