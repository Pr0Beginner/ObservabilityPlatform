package org.zmy.observabilityplatform.incident.infrastructure.repository.memory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.incident.domain.model.MetricKey;
import org.zmy.observabilityplatform.incident.domain.repository.MetricTraceSampleRepository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "local", matchIfMissing = true)
public class InMemoryMetricTraceSampleRepository implements MetricTraceSampleRepository {
    private final Map<String, String> samples = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> recordIfAbsent(MetricKey key, Instant windowStart, String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            samples.putIfAbsent(identity(key, windowStart), traceId);
        }
        return Mono.empty();
    }

    @Override
    public Mono<String> findTraceId(MetricKey key, Instant windowStart) {
        return Mono.justOrEmpty(samples.get(identity(key, windowStart)));
    }

    private String identity(MetricKey key, Instant windowStart) {
        return key.value() + "@" + windowStart;
    }
}
