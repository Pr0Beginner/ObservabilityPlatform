package org.zmy.observabilityplatform.incident.domain.repository;

import org.zmy.observabilityplatform.incident.domain.model.IncidentTraceLink;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface IncidentTraceLinkRepository {
    Mono<Void> link(IncidentTraceLink link);

    Flux<String> findTraceIdsByIncidentId(String incidentId, int limit);

    Flux<String> findIncidentIdsByTraceId(String traceId, int limit);
}
