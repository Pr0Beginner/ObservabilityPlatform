package org.zmy.observabilityplatform.incident.application.query;

import org.zmy.observabilityplatform.incident.domain.model.Incident;
import org.zmy.observabilityplatform.shared.application.query.PageResult;
import reactor.core.publisher.Mono;

public interface IncidentQueryRepository {
    Mono<PageResult<Incident>> search(IncidentSearchQuery query);
}
