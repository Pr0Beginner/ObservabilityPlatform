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
                .filter(entry -> logs.putIfAbsent(entry.id(), entry) == null)
                .toList();
        return Mono.just(inserted);
    }

    @Override
    public Flux<LogEntry> search(LogSearchQuery query) {
        Predicate<LogEntry> predicate = entry -> matches(entry, query);
        return Flux.fromStream(logs.values().stream()
                .filter(predicate)
                .sorted(Comparator.comparing(LogEntry::timestamp).reversed())
                .limit(query.size()));
    }

    @Override
    public Flux<LogEntry> findByIncidentContext(String service, String environment, String fingerprint, int limit) {
        return Flux.fromStream(logs.values().stream()
                .filter(entry -> service.equals(entry.service()))
                .filter(entry -> environment.equals(entry.environment()))
                .filter(entry -> fingerprint.equals(entry.fingerprint()))
                .sorted(Comparator.comparing(LogEntry::timestamp).reversed())
                .limit(limit));
    }

    private boolean matches(LogEntry entry, LogSearchQuery query) {
        return (query.from() == null || !entry.timestamp().isBefore(query.from()))
                && (query.to() == null || !entry.timestamp().isAfter(query.to()))
                && sameIfPresent(query.service(), entry.service())
                && sameIfPresent(query.environment(), entry.environment())
                && sameIfPresent(query.level(), entry.level())
                && sameIfPresent(query.traceId(), entry.traceId())
                && sameIfPresent(query.fingerprint(), entry.fingerprint())
                && (query.keyword() == null || entry.message().toLowerCase().contains(query.keyword().toLowerCase()));
    }

    private boolean sameIfPresent(String expected, String actual) {
        return expected == null || expected.isBlank() || (actual != null && expected.equalsIgnoreCase(actual));
    }
}
