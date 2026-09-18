package org.zmy.observabilityplatform.incident.application.service;

import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.incident.application.dto.IncidentView;
import org.zmy.observabilityplatform.incident.domain.repository.IncidentRepository;
import org.zmy.observabilityplatform.shared.exception.NotFoundException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class IncidentQueryService {
    private final IncidentRepository repository;

    public IncidentQueryService(IncidentRepository repository) {
        this.repository = repository;
    }

    public Flux<IncidentView> findAll() {
        return repository.findAll().map(IncidentView::from);
    }

    public Mono<IncidentView> findById(String id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Incident not found: " + id)))
                .map(IncidentView::from);
    }
}
