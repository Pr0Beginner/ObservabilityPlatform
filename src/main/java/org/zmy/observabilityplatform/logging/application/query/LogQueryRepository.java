package org.zmy.observabilityplatform.logging.application.query;

import org.zmy.observabilityplatform.logging.domain.model.LogEntry;
import reactor.core.publisher.Flux;

public interface LogQueryRepository {
    Flux<LogEntry> search(LogSearchQuery query);

    Flux<LogEntry> findByIncidentContext(String service, String environment, String fingerprint, int limit);
}
