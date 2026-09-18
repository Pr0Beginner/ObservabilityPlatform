package org.zmy.observabilityplatform.incident.domain.repository;

import org.zmy.observabilityplatform.incident.domain.model.Incident;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface IncidentRepository {
    Mono<Incident> save(Incident incident);

    Mono<Incident> findById(String id);

    Mono<Incident> findByDedupKey(String dedupKey);

    Flux<Incident> findAll();
}
