package org.zmy.observabilityplatform.audit.infrastructure.repository.memory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.audit.application.query.AuditQueryRepository;
import org.zmy.observabilityplatform.audit.application.query.AuditSearchQuery;
import org.zmy.observabilityplatform.audit.domain.model.AuditRecord;
import org.zmy.observabilityplatform.audit.domain.repository.AuditRepository;
import org.zmy.observabilityplatform.shared.application.query.PageResult;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "local", matchIfMissing = true)
public class InMemoryAuditRepository implements AuditRepository, AuditQueryRepository {
    private final Map<String, AuditRecord> records = new ConcurrentHashMap<>();

    @Override
    public Mono<AuditRecord> save(AuditRecord record) {
        records.put(record.getId(), record);
        return Mono.just(record);
    }

    @Override
    public Mono<PageResult<AuditRecord>> search(AuditSearchQuery query) {
        List<AuditRecord> matches = records.values().stream()
                .filter(record -> matches(record, query))
                .sorted(Comparator.comparing(AuditRecord::getOccurredAt).reversed()
                        .thenComparing(AuditRecord::getId))
                .toList();
        List<AuditRecord> items = matches.stream()
                .skip(query.offset())
                .limit(query.getSize())
                .toList();
        return Mono.just(PageResult.of(items, query.getPage(), query.getSize(), matches.size()));
    }

    @Override
    public Mono<Long> deleteBefore(Instant cutoff, int limit) {
        if (limit < 1) {
            return Mono.error(new IllegalArgumentException("limit must be positive"));
        }
        List<AuditRecord> expired = records.values().stream()
                .filter(record -> record.getOccurredAt().isBefore(cutoff))
                .sorted(Comparator.comparing(AuditRecord::getOccurredAt))
                .limit(limit)
                .toList();
        long deleted = expired.stream()
                .filter(record -> records.remove(record.getId(), record))
                .count();
        return Mono.just(deleted);
    }

    private boolean matches(AuditRecord record, AuditSearchQuery query) {
        return (query.getActor() == null || query.getActor().equalsIgnoreCase(record.getActor()))
                && (query.getAction() == null || query.getAction() == record.getAction())
                && (query.getTargetType() == null || query.getTargetType() == record.getTargetType())
                && (query.getTargetId() == null || query.getTargetId().equals(record.getTargetId()))
                && (query.getOutcome() == null || query.getOutcome() == record.getOutcome())
                && (query.getFrom() == null || !record.getOccurredAt().isBefore(query.getFrom()))
                && (query.getTo() == null || !record.getOccurredAt().isAfter(query.getTo()));
    }
}
