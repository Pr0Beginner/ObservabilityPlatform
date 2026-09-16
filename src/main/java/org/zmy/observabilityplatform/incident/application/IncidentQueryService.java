package org.zmy.observabilityplatform.incident.application;

import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.incident.domain.Incident;
import org.zmy.observabilityplatform.incident.domain.IncidentRepository;
import org.zmy.observabilityplatform.support.NotFoundException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class IncidentQueryService {
    private final IncidentRepository repository;

    public IncidentQueryService(IncidentRepository repository) {
        this.repository = repository;
    }

    public Flux<Incident> findAll() {
        return repository.findAll();
    }

    public Mono<Incident> findById(String id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Incident not found: " + id)));
    }
}
