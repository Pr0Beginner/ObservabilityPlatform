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
                .sorted(Comparator.comparing(LogEntry::getTimestamp).reversed())
                .limit(query.getSize()));
    }

    @Override
    public Flux<LogEntry> findByIncidentContext(String service, String environment, String fingerprint, int limit) {
        return Flux.fromStream(logs.values().stream()
                .filter(entry -> service.equals(entry.getService()))
                .filter(entry -> environment.equals(entry.getEnvironment()))
                .filter(entry -> fingerprint.equals(entry.getFingerprint()))
                .sorted(Comparator.comparing(LogEntry::getTimestamp).reversed())
                .limit(limit));
    }

    private boolean matches(LogEntry entry, LogSearchQuery query) {
        return (query.getFrom() == null || !entry.getTimestamp().isBefore(query.getFrom()))
                && (query.getTo() == null || !entry.getTimestamp().isAfter(query.getTo()))
                && sameIfPresent(query.getService(), entry.getService())
                && sameIfPresent(query.getEnvironment(), entry.getEnvironment())
                && sameIfPresent(query.getLevel(), entry.getLevel())
                && sameIfPresent(query.getTraceId(), entry.getTraceId())
                && sameIfPresent(query.getFingerprint(), entry.getFingerprint())
                && (query.getKeyword() == null
                || entry.getMessage().toLowerCase().contains(query.getKeyword().toLowerCase()));
    }

    private boolean sameIfPresent(String expected, String actual) {
        return expected == null || expected.isBlank() || (actual != null && expected.equalsIgnoreCase(actual));
    }
}
