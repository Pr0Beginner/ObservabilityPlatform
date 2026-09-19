package org.zmy.observabilityplatform.incident.domain.repository;

import org.zmy.observabilityplatform.incident.domain.model.MetricKey;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface MetricTraceSampleRepository {
    Mono<Void> recordIfAbsent(MetricKey key, Instant windowStart, String traceId);

    Mono<String> findTraceId(MetricKey key, Instant windowStart);
}
