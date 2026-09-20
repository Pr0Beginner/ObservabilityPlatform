package org.zmy.observabilityplatform.incident.domain.repository;

import org.zmy.observabilityplatform.incident.domain.model.MetricKey;
import org.zmy.observabilityplatform.incident.domain.model.MetricWindow;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

public interface MetricWindowRepository {
    Mono<MetricWindow> increment(MetricKey key, Instant windowStart, long delta);

    Mono<Void> incrementAll(List<MetricKey> keys, Instant windowStart);

    Mono<MetricWindow> find(MetricKey key, Instant windowStart);

    Flux<MetricWindow> findByWindow(Instant windowStart);

    Mono<Long> deleteBefore(Instant cutoff, int limit);
}
