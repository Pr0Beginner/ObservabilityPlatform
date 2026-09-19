package org.zmy.observabilityplatform.incident.domain.model;

import lombok.Value;

import java.time.Instant;
import java.util.Objects;

@Value
public class MetricWindow {
    MetricKey key;
    Instant windowStart;
    long count;

    public MetricWindow(MetricKey key, Instant windowStart, long count) {
        this.key = Objects.requireNonNull(key, "key must not be null");
        this.windowStart = Objects.requireNonNull(windowStart, "windowStart must not be null");
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        this.count = count;
    }
}
