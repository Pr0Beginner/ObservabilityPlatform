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
        // 限制单次返回量，防止查询参数直接放大底层存储压力。
        int size = Math.max(1, Math.min(query.getSize(), 200));
        return repository.search(new LogSearchQuery(query.getFrom(), query.getTo(), query.getService(),
                        query.getEnvironment(), query.getLevel(), query.getTraceId(), query.getSpanId(),
                        query.getRequestId(), query.getKeyword(), query.getFingerprint(), size))
                .map(LogView::from);
    }

    public Flux<LogView> findIncidentContext(String service, String environment, String fingerprint, int limit) {
        // 内部诊断查询同样遵守统一的结果上限。
        int boundedLimit = Math.max(1, Math.min(limit, 200));
        return repository.findByIncidentContext(service, environment, fingerprint, boundedLimit)
                .map(LogView::from);
    }

    public Flux<LogView> findByTraceId(String traceId, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 2_001));
        return repository.findByTraceId(traceId, boundedLimit).map(LogView::from);
    }
}
