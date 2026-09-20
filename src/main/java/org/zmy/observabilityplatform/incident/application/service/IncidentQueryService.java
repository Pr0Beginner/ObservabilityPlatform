package org.zmy.observabilityplatform.incident.application.service;

import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.incident.application.dto.IncidentView;
import org.zmy.observabilityplatform.incident.application.query.IncidentQueryRepository;
import org.zmy.observabilityplatform.incident.application.query.IncidentSearchQuery;
import org.zmy.observabilityplatform.incident.domain.repository.IncidentRepository;
import org.zmy.observabilityplatform.shared.application.query.PageResult;
import org.zmy.observabilityplatform.shared.exception.NotFoundException;
import reactor.core.publisher.Mono;

@Service
public class IncidentQueryService {
    private final IncidentRepository repository;
    private final IncidentQueryRepository queryRepository;

    public IncidentQueryService(IncidentRepository repository, IncidentQueryRepository queryRepository) {
        this.repository = repository;
        this.queryRepository = queryRepository;
    }

    public Mono<PageResult<IncidentView>> search(IncidentSearchQuery query) {
        return queryRepository.search(query)
                .map(result -> PageResult.of(result.getItems().stream().map(IncidentView::from).toList(),
                        result.getPage(), result.getSize(), result.getTotalElements()));
    }

    public Mono<IncidentView> findById(String id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Incident not found: " + id)))
                .map(IncidentView::from);
    }
}
