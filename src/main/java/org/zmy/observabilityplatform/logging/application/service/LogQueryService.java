package org.zmy.observabilityplatform.logging.application.service;

import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.logging.application.dto.LogView;
import org.zmy.observabilityplatform.logging.application.query.LogQueryRepository;
import org.zmy.observabilityplatform.logging.application.query.LogSearchQuery;
import reactor.core.publisher.Flux;

@Service
public class LogQueryService {
    private final LogQueryRepository repository;

    public LogQueryService(LogQueryRepository repository) {
        this.repository = repository;
    }

    public Flux<LogView> search(LogSearchQuery query) {
        int size = Math.max(1, Math.min(query.size(), 200));
        return repository.search(new LogSearchQuery(query.from(), query.to(), query.service(), query.environment(),
                        query.level(), query.traceId(), query.keyword(), query.fingerprint(), size))
                .map(LogView::from);
    }

    public Flux<LogView> findIncidentContext(String service, String environment, String fingerprint, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 200));
        return repository.findByIncidentContext(service, environment, fingerprint, boundedLimit)
                .map(LogView::from);
    }
}
