package org.zmy.observabilityplatform.logging.application.query;

import org.zmy.observabilityplatform.logging.domain.model.LogEntry;
import reactor.core.publisher.Flux;

public interface LogQueryRepository {
    Flux<LogEntry> search(LogSearchQuery query);

    Flux<LogEntry> findByIncidentContext(String service, String environment, String fingerprint, int limit);

    Flux<LogEntry> findByTraceId(String traceId, int limit);

    Flux<LogEntry> findByRequestId(String requestId, int limit);
}
