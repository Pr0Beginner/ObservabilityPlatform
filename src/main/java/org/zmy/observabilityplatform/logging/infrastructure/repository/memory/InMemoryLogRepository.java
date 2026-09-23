package org.zmy.observabilityplatform.logging.infrastructure.repository.memory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.logging.application.query.LogQueryRepository;
import org.zmy.observabilityplatform.logging.application.query.LogSearchQuery;
import org.zmy.observabilityplatform.logging.domain.model.LogEntry;
import org.zmy.observabilityplatform.logging.domain.repository.LogRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "local", matchIfMissing = true)
public class InMemoryLogRepository implements LogRepository, LogQueryRepository {
    private static final Comparator<LogEntry> LOG_ORDER = Comparator
            .comparingLong((LogEntry entry) -> entry.getTimestamp().toEpochMilli()).reversed()
            .thenComparing(LogEntry::getId, Comparator.reverseOrder());

    private final Map<String, LogEntry> logs = new ConcurrentHashMap<>();

    @Override
    public Mono<List<LogEntry>> saveAll(List<LogEntry> entries) {
        List<LogEntry> inserted = entries.stream()
                .filter(entry -> logs.putIfAbsent(entry.getId(), entry) == null)
                .toList();
        return Mono.just(inserted);
    }

    @Override
    public Flux<LogEntry> search(LogSearchQuery query) {
        Predicate<LogEntry> predicate = entry -> matches(entry, query);
        return Flux.fromStream(logs.values().stream()
                .filter(predicate)
                .filter(entry -> afterCursor(entry, query))
                .sorted(LOG_ORDER)
                .limit(query.getSize()));
    }

    @Override
    public Flux<LogEntry> findByIncidentContext(String service, String environment, String fingerprint, int limit) {
        return Flux.fromStream(logs.values().stream()
                .filter(entry -> service.equals(entry.getService()))
                .filter(entry -> environment.equals(entry.getEnvironment()))
                .filter(entry -> fingerprint.equals(entry.getFingerprint()))
                .sorted(LOG_ORDER)
                .limit(limit));
    }

    @Override
    public Flux<LogEntry> findByTraceId(String traceId, int limit) {
        return Flux.fromStream(logs.values().stream()
                .filter(entry -> traceId.equals(entry.getTraceId()))
                .sorted(LOG_ORDER)
                .limit(limit));
    }

    @Override
    public Flux<LogEntry> findByRequestId(String requestId, int limit) {
        return Flux.fromStream(logs.values().stream()
                .filter(entry -> requestId.equals(entry.getRequestId()))
                .sorted(LOG_ORDER)
                .limit(limit));
    }

    private boolean matches(LogEntry entry, LogSearchQuery query) {
        return (query.getFrom() == null || !entry.getTimestamp().isBefore(query.getFrom()))
                && (query.getTo() == null || !entry.getTimestamp().isAfter(query.getTo()))
                && sameIfPresent(query.getService(), entry.getService())
                && sameIfPresent(query.getEnvironment(), entry.getEnvironment())
                && sameIfPresent(query.getLevel(), entry.getLevel())
                && sameIfPresent(query.getTraceId(), entry.getTraceId())
                && sameIfPresent(query.getSpanId(), entry.getSpanId())
                && sameIfPresent(query.getRequestId(), entry.getRequestId())
                && sameIfPresent(query.getFingerprint(), entry.getFingerprint())
                && (query.getKeyword() == null
                || entry.getMessage().toLowerCase().contains(query.getKeyword().toLowerCase()));
    }

    private boolean sameIfPresent(String expected, String actual) {
        return expected == null || expected.isBlank() || (actual != null && expected.equalsIgnoreCase(actual));
    }

    private boolean afterCursor(LogEntry entry, LogSearchQuery query) {
        if (query.getCursor() == null) {
            return true;
        }
        // 与 Elasticsearch search_after 保持相同边界，时间相同时用日志 ID 消除歧义。
        long timestamp = entry.getTimestamp().toEpochMilli();
        long cursorTimestamp = query.getCursor().getTimestampEpochMillis();
        return timestamp < cursorTimestamp
                || timestamp == cursorTimestamp && entry.getId().compareTo(query.getCursor().getId()) < 0;
    }
}
