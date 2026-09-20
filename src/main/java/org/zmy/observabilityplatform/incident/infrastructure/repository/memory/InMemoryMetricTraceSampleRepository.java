package org.zmy.observabilityplatform.incident.infrastructure.repository.memory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.incident.domain.model.MetricKey;
import org.zmy.observabilityplatform.incident.domain.repository.MetricTraceSampleRepository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "local", matchIfMissing = true)
public class InMemoryMetricTraceSampleRepository implements MetricTraceSampleRepository {
    private final Map<String, StoredSample> samples = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> recordIfAbsent(MetricKey key, Instant windowStart, String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            samples.putIfAbsent(identity(key, windowStart), new StoredSample(windowStart, traceId));
        }
        return Mono.empty();
    }

    @Override
    public Mono<String> findTraceId(MetricKey key, Instant windowStart) {
        StoredSample sample = samples.get(identity(key, windowStart));
        return sample == null ? Mono.empty() : Mono.just(sample.traceId);
    }

    @Override
    public Mono<Long> deleteBefore(Instant cutoff, int limit) {
        if (limit < 1) {
            return Mono.error(new IllegalArgumentException("limit must be positive"));
        }
        long deleted = samples.entrySet().stream()
                .filter(entry -> entry.getValue().windowStart.isBefore(cutoff))
                .sorted(Comparator.comparing(entry -> entry.getValue().windowStart))
                .limit(limit)
                .filter(entry -> samples.remove(entry.getKey(), entry.getValue()))
                .count();
        return Mono.just(deleted);
    }

    private String identity(MetricKey key, Instant windowStart) {
        return key.value() + "@" + windowStart;
    }

    private static final class StoredSample {
        private final Instant windowStart;
        private final String traceId;

        private StoredSample(Instant windowStart, String traceId) {
            this.windowStart = windowStart;
            this.traceId = traceId;
        }
    }
}
