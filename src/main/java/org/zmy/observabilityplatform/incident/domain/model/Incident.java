package org.zmy.observabilityplatform.incident.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;

@Getter
@ToString
@EqualsAndHashCode
public final class Incident {
    private static final String DEFAULT_ASSIGNEE = "unassigned";

    private final String id;
    private final String dedupKey;
    private final String title;
    private final String service;
    private final String environment;
    private final String fingerprint;
    private final IncidentSeverity severity;
    private final IncidentStatus status;
    private final Instant startedAt;
    private final Instant updatedAt;
    private final long errorCount;
    private final String assignee;
    private final String resolution;

    private Incident(String id, String dedupKey, String title, String service, String environment,
                     String fingerprint, IncidentSeverity severity, IncidentStatus status,
                     Instant startedAt, Instant updatedAt, long errorCount, String assignee,
                     String resolution) {
        this.id = requireText(id, "id");
        this.dedupKey = requireText(dedupKey, "dedupKey");
        this.title = requireText(title, "title");
        this.service = requireText(service, "service");
        this.environment = requireText(environment, "environment");
        this.fingerprint = requireText(fingerprint, "fingerprint");
        this.severity = Objects.requireNonNull(severity, "severity must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (errorCount < 1) {
            throw new IllegalArgumentException("errorCount must be positive");
        }
        if (updatedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("updatedAt must not be before startedAt");
        }
        if (status == IncidentStatus.CLOSED && (resolution == null || resolution.isBlank())) {
            throw new IllegalArgumentException("A resolution is required for a closed incident");
        }
        this.errorCount = errorCount;
        this.assignee = requireText(assignee, "assignee");
        this.resolution = blankToNull(resolution);
    }

    public static Incident open(String id, String dedupKey, String service, String environment,
                                String fingerprint, String level, long errorCount, Instant startedAt,
                                Instant openedAt) {
        String normalizedLevel = requireText(level, "level").toUpperCase();
        String title = "Repeated " + normalizedLevel + " logs in " + requireText(service, "service");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(openedAt, "openedAt must not be null");
        Instant effectiveOpenedAt = openedAt.isBefore(startedAt) ? startedAt : openedAt;
        return new Incident(id, dedupKey, title, service, environment, fingerprint, IncidentSeverity.P2,
                IncidentStatus.OPEN, startedAt, effectiveOpenedAt, errorCount, DEFAULT_ASSIGNEE, null);
    }

    public static Incident restore(String id, String dedupKey, String title, String service, String environment,
                                   String fingerprint, IncidentSeverity severity, IncidentStatus status,
                                   Instant startedAt, Instant updatedAt, long errorCount, String assignee,
                                   String resolution) {
        return new Incident(id, dedupKey, title, service, environment, fingerprint, severity, status,
                startedAt, updatedAt, errorCount, assignee, resolution);
    }

    public Incident transitionTo(IncidentStatus target, String resolution, Instant changedAt) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(changedAt, "changedAt must not be null");
        requireCurrentOrLater(changedAt);
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Illegal incident transition: " + status + " -> " + target);
        }
        if (target == IncidentStatus.CLOSED && (resolution == null || resolution.isBlank())) {
            throw new IllegalStateException("A resolution is required before closing an incident");
        }
        return new Incident(id, dedupKey, title, service, environment, fingerprint, severity,
                target, startedAt, changedAt, errorCount, assignee, resolution);
    }

    public Incident registerOccurrences(long count, Instant observedAt) {
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        if (count < 1) {
            throw new IllegalArgumentException("count must be positive");
        }
        requireCurrentOrLater(observedAt);
        return new Incident(id, dedupKey, title, service, environment, fingerprint, severity,
                status, startedAt, observedAt, Math.max(errorCount, count), assignee, resolution);
    }

    public Incident assignTo(String newAssignee, Instant changedAt) {
        requireCurrentOrLater(changedAt);
        return new Incident(id, dedupKey, title, service, environment, fingerprint, severity,
                status, startedAt, changedAt, errorCount, requireText(newAssignee, "assignee"), resolution);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private void requireCurrentOrLater(Instant changedAt) {
        Objects.requireNonNull(changedAt, "changedAt must not be null");
        if (changedAt.isBefore(updatedAt)) {
            throw new IllegalArgumentException("changedAt must not be before the current updatedAt");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
