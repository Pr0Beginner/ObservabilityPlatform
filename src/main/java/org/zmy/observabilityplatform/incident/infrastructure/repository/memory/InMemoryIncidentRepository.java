package org.zmy.observabilityplatform.incident.infrastructure.repository.memory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
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
    public Mono<Incident> save(Incident incident) {
        if (incidents.containsKey(incident.id())) {
            incidents.put(incident.id(), incident);
            return Mono.just(incident);
        }
        Incident existing = incidents.values().stream()
                .filter(value -> value.dedupKey().equals(incident.dedupKey()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return Mono.just(existing);
        }
        incidents.put(incident.id(), incident);
        return Mono.just(incident);
    }

    @Override
    public Mono<Incident> findById(String id) {
        return Mono.justOrEmpty(incidents.get(id));
    }

    @Override
    public Mono<Incident> findByDedupKey(String dedupKey) {
        return Flux.fromIterable(incidents.values())
                .filter(incident -> incident.dedupKey().equals(dedupKey))
                .next();
    }

    @Override
    public Flux<Incident> findAll() {
        return Flux.fromStream(incidents.values().stream()
                .sorted(Comparator.comparing(Incident::startedAt).reversed()));
    }
}
