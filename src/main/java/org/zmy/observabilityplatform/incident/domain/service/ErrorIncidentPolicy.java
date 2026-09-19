package org.zmy.observabilityplatform.incident.domain.service;

import org.zmy.observabilityplatform.incident.domain.model.Incident;
import org.zmy.observabilityplatform.shared.exception.BusinessConflictException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class ErrorIncidentPolicy {
    private final int threshold;

    public ErrorIncidentPolicy(int threshold) {
        if (threshold < 1) {
            throw new IllegalArgumentException("threshold must be positive");
        }
        this.threshold = threshold;
    }

    public boolean observes(String level) {
        return "ERROR".equals(level) || "FATAL".equals(level);
    }

    public Instant windowOf(Instant timestamp) {
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
        return timestamp.truncatedTo(ChronoUnit.MINUTES);
    }

    public String dedupKey(String service, String environment, String fingerprint, Instant window) {
        return String.join("|", service, environment, fingerprint, window.toString());
    }

    public boolean hasReachedThreshold(long count) {
        return count >= threshold;
    }

    public Incident openIncident(String id, String dedupKey, String service, String environment,
                                 String fingerprint, String level, long count, Instant window,
                                 Instant openedAt) {
        if (!observes(level)) {
            throw new IllegalArgumentException("Only ERROR or FATAL logs can open an incident");
        }
        if (!hasReachedThreshold(count)) {
            throw new BusinessConflictException("The error threshold has not been reached");
        }
        return Incident.open(id, dedupKey, service, environment, fingerprint, level, count, window, openedAt);
    }
}
