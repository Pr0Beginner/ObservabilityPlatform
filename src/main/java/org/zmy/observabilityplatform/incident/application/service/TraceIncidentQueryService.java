package org.zmy.observabilityplatform.incident.application.service;

import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.incident.application.dto.IncidentView;
import org.zmy.observabilityplatform.incident.domain.repository.IncidentRepository;
import org.zmy.observabilityplatform.incident.domain.repository.IncidentTraceLinkRepository;
import reactor.core.publisher.Flux;

@Service
public class TraceIncidentQueryService {
    private final IncidentTraceLinkRepository linkRepository;
    private final IncidentRepository incidentRepository;

    public TraceIncidentQueryService(IncidentTraceLinkRepository linkRepository,
                                     IncidentRepository incidentRepository) {
        this.linkRepository = linkRepository;
        this.incidentRepository = incidentRepository;
    }

    public Flux<IncidentView> findByTraceId(String traceId, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        return linkRepository.findIncidentIdsByTraceId(traceId, limit)
                .concatMap(incidentRepository::findById)
                .map(IncidentView::from);
    }
}
