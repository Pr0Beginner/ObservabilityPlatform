package org.zmy.observabilityplatform.incident.domain;

import java.time.Instant;

public record Incident(
        String id,
        String dedupKey,
        String title,
        String service,
        String environment,
        String fingerprint,
        IncidentSeverity severity,
        IncidentStatus status,
        Instant startedAt,
        Instant updatedAt,
        long errorCount,
        String assignee,
        String resolution) {

    public Incident transitionTo(IncidentStatus target, String resolution) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Illegal incident transition: " + status + " -> " + target);
        }
        if (target == IncidentStatus.CLOSED && (resolution == null || resolution.isBlank())) {
            throw new IllegalStateException("A resolution is required before closing an incident");
        }
        return new Incident(id, dedupKey, title, service, environment, fingerprint, severity,
                target, startedAt, Instant.now(), errorCount, assignee, resolution);
    }

    public Incident withErrorCount(long count) {
        return new Incident(id, dedupKey, title, service, environment, fingerprint, severity,
                status, startedAt, Instant.now(), Math.max(errorCount, count), assignee, resolution);
    }
}
