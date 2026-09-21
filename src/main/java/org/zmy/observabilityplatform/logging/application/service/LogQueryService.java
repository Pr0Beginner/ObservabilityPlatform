package org.zmy.observabilityplatform.logging.application.service;

import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.logging.application.dto.LogSearchPage;
import org.zmy.observabilityplatform.logging.application.dto.LogView;
import org.zmy.observabilityplatform.logging.application.query.LogCursor;
import org.zmy.observabilityplatform.logging.application.query.LogCursorCodec;
import org.zmy.observabilityplatform.logging.application.query.LogQueryRepository;
import org.zmy.observabilityplatform.logging.application.query.LogSearchQuery;
import org.zmy.observabilityplatform.logging.domain.model.LogEntry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class LogQueryService {
    private final LogQueryRepository repository;
    private final LogCursorCodec cursorCodec;

    public LogQueryService(LogQueryRepository repository, LogCursorCodec cursorCodec) {
        this.repository = repository;
        this.cursorCodec = cursorCodec;
    }

    public Mono<LogSearchPage> search(LogSearchQuery query) {
        // 多取一条用于判断是否存在下一页，避免执行额外的计数查询。
        int size = Math.max(1, Math.min(query.getSize(), 200));
        return repository.search(new LogSearchQuery(query.getFrom(), query.getTo(), query.getService(),
                        query.getEnvironment(), query.getLevel(), query.getTraceId(), query.getSpanId(),
                        query.getRequestId(), query.getKeyword(), query.getFingerprint(), query.getCursor(),
                        size + 1))
                .collectList()
                .map(entries -> toPage(entries, size));
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

    private LogSearchPage toPage(List<LogEntry> entries, int size) {
        boolean hasMore = entries.size() > size;
        List<LogEntry> selected = hasMore ? entries.subList(0, size) : entries;
        List<LogView> items = selected.stream().map(LogView::from).toList();
        String nextCursor = hasMore && !selected.isEmpty()
                ? cursorCodec.encode(cursorOf(selected.get(selected.size() - 1))) : null;
        return new LogSearchPage(items, nextCursor, hasMore);
    }

    private LogCursor cursorOf(LogEntry entry) {
        return new LogCursor(entry.getTimestamp().toEpochMilli(), entry.getId());
    }
}
