package org.zmy.observabilityplatform.incident.domain;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface IncidentRepository {
    Mono<Incident> save(Incident incident);

    Mono<Incident> findById(String id);

    Mono<Incident> findByDedupKey(String dedupKey);

    Flux<Incident> findAll();
}
