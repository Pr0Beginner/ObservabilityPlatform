package org.zmy.observabilityplatform.incident.application.service;

import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.logging.application.dto.LogView;
import org.zmy.observabilityplatform.logging.application.service.LogQueryService;
import org.zmy.observabilityplatform.logging.application.service.TraceQueryService;
import org.zmy.observabilityplatform.logging.domain.model.TraceCallTree;
import org.zmy.observabilityplatform.incident.domain.repository.IncidentTraceLinkRepository;
import reactor.core.publisher.Flux;

@Service
public class IncidentTraceQueryService {
    private final IncidentQueryService incidentQueryService;
    private final LogQueryService logQueryService;
    private final TraceQueryService traceQueryService;
    private final IncidentTraceLinkRepository traceLinkRepository;

    public IncidentTraceQueryService(IncidentQueryService incidentQueryService,
                                     LogQueryService logQueryService,
                                     TraceQueryService traceQueryService,
                                     IncidentTraceLinkRepository traceLinkRepository) {
        this.incidentQueryService = incidentQueryService;
        this.logQueryService = logQueryService;
        this.traceQueryService = traceQueryService;
        this.traceLinkRepository = traceLinkRepository;
    }

    public Flux<TraceCallTree> findRelated(String incidentId, int requestedTraceLimit, int requestedLogLimit) {
        int traceLimit = Math.max(1, Math.min(requestedTraceLimit, 20));
        int logLimit = Math.max(1, Math.min(requestedLogLimit, 2_000));
        Flux<String> persistedLinks = traceLinkRepository.findTraceIdsByIncidentId(incidentId, traceLimit);
        Flux<String> legacyLinks = incidentQueryService.findById(incidentId)
                .filter(incident -> incident.getFingerprint() != null)
                .flatMapMany(incident -> logQueryService.findIncidentContext(incident.getService(),
                                incident.getEnvironment(), incident.getFingerprint(), 200)
                        .map(LogView::getTraceId)
                        .filter(traceId -> traceId != null && !traceId.isBlank())
                        .distinct().take(traceLimit));
        return persistedLinks.switchIfEmpty(legacyLinks)
                .distinct().take(traceLimit)
                .concatMap(traceId -> traceQueryService.findById(traceId, logLimit));
    }

    public Flux<LogView> findRelatedLogs(String incidentId, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 200));
        Flux<LogView> persistedLogs = traceLinkRepository.findTraceIdsByIncidentId(incidentId, 20)
                .concatMap(traceId -> logQueryService.findByTraceId(traceId, limit))
                .take(limit);
        Flux<LogView> legacyLogs = incidentQueryService.findById(incidentId)
                .filter(incident -> incident.getFingerprint() != null)
                .flatMapMany(incident -> logQueryService.findIncidentContext(incident.getService(),
                        incident.getEnvironment(), incident.getFingerprint(), limit));
        return persistedLogs.switchIfEmpty(legacyLogs);
    }
}
