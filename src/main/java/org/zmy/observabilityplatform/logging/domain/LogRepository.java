package org.zmy.observabilityplatform.logging.domain;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface LogRepository {
    Mono<List<LogEntry>> saveAll(List<LogEntry> entries);

    Flux<LogEntry> search(LogSearchQuery query);

    Flux<LogEntry> findByIncidentContext(String service, String environment, String fingerprint, int limit);
}
