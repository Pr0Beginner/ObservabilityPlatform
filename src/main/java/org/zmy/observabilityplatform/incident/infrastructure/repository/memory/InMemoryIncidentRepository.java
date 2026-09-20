package org.zmy.observabilityplatform.incident.infrastructure.repository.memory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.incident.domain.exception.IncidentVersionConflictException;
import org.zmy.observabilityplatform.incident.application.query.IncidentQueryRepository;
import org.zmy.observabilityplatform.incident.application.query.IncidentSearchQuery;
import org.zmy.observabilityplatform.incident.domain.model.Incident;
import org.zmy.observabilityplatform.incident.domain.repository.IncidentRepository;
import org.zmy.observabilityplatform.shared.application.query.PageResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "local", matchIfMissing = true)
public class InMemoryIncidentRepository implements IncidentRepository, IncidentQueryRepository {
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

    @Override
    public Mono<PageResult<Incident>> search(IncidentSearchQuery query) {
        Predicate<Incident> predicate = incident -> matches(incident, query);
        List<Incident> matches = incidents.values().stream()
                .filter(predicate)
                .sorted(Comparator.comparing(Incident::getStartedAt).reversed()
                        .thenComparing(Incident::getId))
                .toList();
        List<Incident> pageItems = matches.stream()
                .skip(query.offset())
                .limit(query.getSize())
                .toList();
        return Mono.just(PageResult.of(pageItems, query.getPage(), query.getSize(), matches.size()));
    }

    private boolean matches(Incident incident, IncidentSearchQuery query) {
        return (query.getStatus() == null || query.getStatus() == incident.getStatus())
                && (query.getType() == null || query.getType() == incident.getType())
                && sameIfPresent(query.getService(), incident.getService())
                && sameIfPresent(query.getEnvironment(), incident.getEnvironment())
                && sameIfPresent(query.getAssignee(), incident.getAssignee())
                && (query.getStartedFrom() == null || !incident.getStartedAt().isBefore(query.getStartedFrom()))
                && (query.getStartedTo() == null || !incident.getStartedAt().isAfter(query.getStartedTo()));
    }

    private boolean sameIfPresent(String expected, String actual) {
        return expected == null || expected.equalsIgnoreCase(actual);
    }
}
