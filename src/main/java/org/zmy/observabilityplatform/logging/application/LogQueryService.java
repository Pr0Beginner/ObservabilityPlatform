package org.zmy.observabilityplatform.logging.application;

import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.logging.domain.LogEntry;
import org.zmy.observabilityplatform.logging.domain.LogRepository;
import org.zmy.observabilityplatform.logging.domain.LogSearchQuery;
import reactor.core.publisher.Flux;

@Service
public class LogQueryService {
    private final LogRepository repository;

    public LogQueryService(LogRepository repository) {
        this.repository = repository;
    }

    public Flux<LogEntry> search(LogSearchQuery query) {
        int size = Math.max(1, Math.min(query.size(), 200));
        return repository.search(new LogSearchQuery(query.from(), query.to(), query.service(), query.environment(),
                query.level(), query.traceId(), query.keyword(), query.fingerprint(), size));
    }
}
