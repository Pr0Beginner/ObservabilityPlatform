package org.zmy.observabilityplatform.incident.domain.service;

import java.time.Instant;

public final class MetricWindowPolicy {
    private final long windowSeconds;

    public MetricWindowPolicy(int windowMinutes) {
        if (windowMinutes < 1) {
            throw new IllegalArgumentException("windowMinutes must be positive");
        }
        this.windowSeconds = windowMinutes * 60L;
    }

    public Instant windowOf(Instant timestamp) {
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
        long epochSecond = timestamp.getEpochSecond();
        return Instant.ofEpochSecond(Math.floorDiv(epochSecond, windowSeconds) * windowSeconds);
    }

    public Instant previousCompletedWindow(Instant now) {
        return windowOf(now).minusSeconds(windowSeconds);
    }
}
