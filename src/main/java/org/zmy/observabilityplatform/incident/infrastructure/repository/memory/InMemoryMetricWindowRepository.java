package org.zmy.observabilityplatform.incident.infrastructure.repository.memory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.incident.domain.model.MetricKey;
import org.zmy.observabilityplatform.incident.domain.model.MetricWindow;
import org.zmy.observabilityplatform.incident.domain.repository.MetricWindowRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "local", matchIfMissing = true)
public class InMemoryMetricWindowRepository implements MetricWindowRepository {
    private final Map<String, StoredWindow> values = new ConcurrentHashMap<>();

    @Override
    public Mono<MetricWindow> increment(MetricKey key, Instant windowStart, long delta) {
        if (delta < 1) {
            return Mono.error(new IllegalArgumentException("delta must be positive"));
        }
        StoredWindow stored = values.computeIfAbsent(identity(key, windowStart),
                ignored -> new StoredWindow(key, windowStart));
        long count = stored.count.addAndGet(delta);
        return Mono.just(new MetricWindow(key, windowStart, count));
    }

    @Override
    public Mono<Void> incrementAll(List<MetricKey> keys, Instant windowStart) {
        if (keys == null || keys.isEmpty()) {
            return Mono.error(new IllegalArgumentException("keys must not be empty"));
        }
        keys.forEach(key -> values.computeIfAbsent(identity(key, windowStart),
                ignored -> new StoredWindow(key, windowStart)).count.incrementAndGet());
        return Mono.empty();
    }

    @Override
    public Mono<MetricWindow> find(MetricKey key, Instant windowStart) {
        StoredWindow value = values.get(identity(key, windowStart));
        return value == null ? Mono.empty() : Mono.just(value.toMetricWindow());
    }

    @Override
    public Flux<MetricWindow> findByWindow(Instant windowStart) {
        return Flux.fromIterable(values.entrySet())
                .map(Map.Entry::getValue)
                .filter(entry -> entry.windowStart.equals(windowStart))
                .map(StoredWindow::toMetricWindow);
    }

    @Override
    public Mono<Long> deleteBefore(Instant cutoff, int limit) {
        if (limit < 1) {
            return Mono.error(new IllegalArgumentException("limit must be positive"));
        }
        long deleted = values.entrySet().stream()
                .filter(entry -> entry.getValue().windowStart.isBefore(cutoff))
                .sorted(Comparator.comparing(entry -> entry.getValue().windowStart))
                .limit(limit)
                .filter(entry -> values.remove(entry.getKey(), entry.getValue()))
                .count();
        return Mono.just(deleted);
    }

    private String identity(MetricKey key, Instant windowStart) {
        return key.value() + "@" + windowStart;
    }

    private static final class StoredWindow {
        private final MetricKey key;
        private final Instant windowStart;
        private final AtomicLong count = new AtomicLong();

        private StoredWindow(MetricKey key, Instant windowStart) {
            this.key = key;
            this.windowStart = windowStart;
        }

        private MetricWindow toMetricWindow() {
            return new MetricWindow(key, windowStart, count.get());
        }
    }
}
