package org.zmy.observabilityplatform.incident.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.zmy.observabilityplatform.shared.exception.BusinessConflictException;

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
    private final IncidentType type;
    private final String operation;
    private final String dimension;
    private final IncidentSeverity severity;
    private final IncidentStatus status;
    private final Instant startedAt;
    private final Instant updatedAt;
    private final long errorCount;
    private final String assignee;
    private final String resolution;
    private final Double currentValue;
    private final Double baselineValue;
    private final Instant recoveredAt;
    private final int healthyWindowCount;
    private final Instant lastObservedWindow;
    private final AnomalyPolicyReference policyReference;
    private final long version;

    private Incident(String id, String dedupKey, String title, String service, String environment,
                     String fingerprint, IncidentType type, String operation, String dimension,
                     IncidentSeverity severity, IncidentStatus status,
                     Instant startedAt, Instant updatedAt, long errorCount, String assignee,
                     String resolution, Double currentValue, Double baselineValue,
                     Instant recoveredAt, int healthyWindowCount, Instant lastObservedWindow,
                     AnomalyPolicyReference policyReference, long version) {
        this.id = requireText(id, "id");
        this.dedupKey = requireText(dedupKey, "dedupKey");
        this.title = requireText(title, "title");
        this.service = requireText(service, "service");
        this.environment = requireText(environment, "environment");
        this.fingerprint = blankToNull(fingerprint);
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.operation = blankToNull(operation);
        this.dimension = blankToNull(dimension);
        this.severity = Objects.requireNonNull(severity, "severity must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (errorCount < 0) {
            throw new IllegalArgumentException("errorCount must not be negative");
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
        this.currentValue = currentValue;
        this.baselineValue = baselineValue;
        this.recoveredAt = recoveredAt;
        if (healthyWindowCount < 0) {
            throw new IllegalArgumentException("healthyWindowCount must not be negative");
        }
        this.healthyWindowCount = healthyWindowCount;
        this.lastObservedWindow = lastObservedWindow;
        this.policyReference = Objects.requireNonNull(policyReference, "policyReference must not be null");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        this.version = version;
    }

    public static Incident open(String id, String dedupKey, String service, String environment,
                                String fingerprint, String level, long errorCount, Instant startedAt,
                                Instant openedAt, AnomalyPolicyReference policyReference) {
        String normalizedLevel = requireText(level, "level").toUpperCase();
        String title = "Repeated " + normalizedLevel + " logs in " + requireText(service, "service");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(openedAt, "openedAt must not be null");
        Instant effectiveOpenedAt = openedAt.isBefore(startedAt) ? startedAt : openedAt;
        return new Incident(id, dedupKey, title, service, environment, fingerprint,
                IncidentType.REPEATED_ERROR, null, fingerprint, IncidentSeverity.P2,
                IncidentStatus.OPEN, startedAt, effectiveOpenedAt, errorCount, DEFAULT_ASSIGNEE, null,
                (double) errorCount, null, null, 0, startedAt, policyReference, 0);
    }

    public static Incident openAnomaly(String id, String dedupKey, IncidentType type, String title,
                                       String service, String environment, String operation, String dimension,
                                       double currentValue, Double baselineValue, Instant windowStart,
                                       Instant openedAt, AnomalyPolicyReference policyReference) {
        if (type == IncidentType.REPEATED_ERROR) {
            throw new IllegalArgumentException("Use open for repeated error incidents");
        }
        return new Incident(id, dedupKey, title, service, environment, null, type, operation, dimension,
                IncidentSeverity.P2, IncidentStatus.OPEN, windowStart, openedAt, 0, DEFAULT_ASSIGNEE,
                null, currentValue, baselineValue, null, 0, windowStart, policyReference, 0);
    }

    public static Incident restore(String id, String dedupKey, String title, String service, String environment,
                                   String fingerprint, IncidentType type, String operation, String dimension,
                                   IncidentSeverity severity, IncidentStatus status,
                                   Instant startedAt, Instant updatedAt, long errorCount, String assignee,
                                   String resolution, Double currentValue, Double baselineValue,
                                   Instant recoveredAt, int healthyWindowCount, Instant lastObservedWindow,
                                   AnomalyPolicyReference policyReference, long version) {
        return new Incident(id, dedupKey, title, service, environment, fingerprint, type, operation, dimension,
                severity, status, startedAt, updatedAt, errorCount, assignee, resolution, currentValue,
                baselineValue, recoveredAt, healthyWindowCount, lastObservedWindow, policyReference, version);
    }

    public Incident transitionTo(IncidentStatus target, String resolution, Instant changedAt) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(changedAt, "changedAt must not be null");
        requireCurrentOrLater(changedAt);
        if (!status.canTransitionTo(target)) {
            throw new BusinessConflictException("Illegal incident transition: " + status + " -> " + target);
        }
        if (target == IncidentStatus.CLOSED && (resolution == null || resolution.isBlank())) {
            throw new BusinessConflictException("A resolution is required before closing an incident");
        }
        Instant nextRecoveredAt = target == IncidentStatus.RESOLVED ? changedAt
                : target == IncidentStatus.OPEN ? null : recoveredAt;
        return copy(target, changedAt, errorCount, assignee, resolution, currentValue, baselineValue,
                nextRecoveredAt, target == IncidentStatus.OPEN ? 0 : healthyWindowCount,
                lastObservedWindow, policyReference);
    }

    public Incident registerOccurrences(long count, Instant windowStart, Instant observedAt,
                                        AnomalyPolicyReference observedPolicy) {
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        Objects.requireNonNull(windowStart, "windowStart must not be null");
        if (count < 1) {
            throw new IllegalArgumentException("count must be positive");
        }
        requireCurrentOrLater(observedAt);
        if (lastObservedWindow != null && windowStart.isBefore(lastObservedWindow)) {
            return this;
        }
        long alreadyObserved = windowStart.equals(lastObservedWindow) && currentValue != null
                ? currentValue.longValue() : 0;
        long observedInWindow = Math.max(count, alreadyObserved);
        long nextErrorCount = errorCount + Math.max(0, observedInWindow - alreadyObserved);
        IncidentStatus nextStatus = status == IncidentStatus.RESOLVED || status == IncidentStatus.CLOSED
                ? IncidentStatus.OPEN : status;
        return copy(nextStatus, observedAt, nextErrorCount, assignee,
                nextStatus == IncidentStatus.OPEN ? null : resolution, (double) observedInWindow, baselineValue,
                nextStatus == IncidentStatus.OPEN ? null : recoveredAt, 0, windowStart, observedPolicy);
    }

    public Incident observeAnomaly(double current, Double baseline, Instant windowStart, Instant observedAt,
                                   AnomalyPolicyReference observedPolicy) {
        requireCurrentOrLater(observedAt);
        if (lastObservedWindow != null && !windowStart.isAfter(lastObservedWindow)) {
            return this;
        }
        IncidentStatus nextStatus = status == IncidentStatus.RESOLVED || status == IncidentStatus.CLOSED
                ? IncidentStatus.OPEN : status;
        return copy(nextStatus, observedAt, errorCount, assignee,
                nextStatus == IncidentStatus.OPEN ? null : resolution, current, baseline,
                nextStatus == IncidentStatus.OPEN ? null : recoveredAt, 0, windowStart, observedPolicy);
    }

    public Incident registerHealthyWindow(int requiredConsecutiveWindows, Instant windowStart, Instant observedAt,
                                          AnomalyPolicyReference observedPolicy) {
        return registerHealthyWindow(requiredConsecutiveWindows, windowStart, observedAt,
                currentValue, baselineValue, observedPolicy);
    }

    public Incident registerHealthyWindow(int requiredConsecutiveWindows, Instant windowStart, Instant observedAt,
                                           Double healthyCurrentValue, Double healthyBaselineValue,
                                           AnomalyPolicyReference observedPolicy) {
        if (requiredConsecutiveWindows < 1) {
            throw new IllegalArgumentException("requiredConsecutiveWindows must be positive");
        }
        requireCurrentOrLater(observedAt);
        if (lastObservedWindow != null && !windowStart.isAfter(lastObservedWindow)) {
            return this;
        }
        if (status == IncidentStatus.RESOLVED || status == IncidentStatus.CLOSED) {
            return this;
        }
        int nextCount = healthyWindowCount + 1;
        if (nextCount >= requiredConsecutiveWindows) {
            return copy(IncidentStatus.RESOLVED, observedAt, errorCount, assignee,
                    "Recovered automatically after healthy observation windows", healthyCurrentValue,
                    healthyBaselineValue, observedAt, nextCount, windowStart, observedPolicy);
        }
        return copy(status, observedAt, errorCount, assignee, resolution, healthyCurrentValue,
                healthyBaselineValue, recoveredAt, nextCount, windowStart, observedPolicy);
    }

    public Incident assignTo(String newAssignee, Instant changedAt) {
        requireCurrentOrLater(changedAt);
        return copy(status, changedAt, errorCount, requireText(newAssignee, "assignee"), resolution,
                currentValue, baselineValue, recoveredAt, healthyWindowCount,
                lastObservedWindow, policyReference);
    }

    public Incident persistedAtVersion(long persistedVersion) {
        if (persistedVersion < 1) {
            throw new IllegalArgumentException("persistedVersion must be positive");
        }
        return new Incident(id, dedupKey, title, service, environment, fingerprint, type, operation, dimension,
                severity, status, startedAt, updatedAt, errorCount, assignee, resolution, currentValue,
                baselineValue, recoveredAt, healthyWindowCount, lastObservedWindow,
                policyReference, persistedVersion);
    }

    private Incident copy(IncidentStatus nextStatus, Instant nextUpdatedAt, long nextErrorCount,
                          String nextAssignee, String nextResolution, Double nextCurrentValue,
                          Double nextBaselineValue, Instant nextRecoveredAt, int nextHealthyWindowCount,
                          Instant nextLastObservedWindow, AnomalyPolicyReference nextPolicyReference) {
        return new Incident(id, dedupKey, title, service, environment, fingerprint, type, operation, dimension,
                severity, nextStatus, startedAt, nextUpdatedAt, nextErrorCount, nextAssignee, nextResolution,
                nextCurrentValue, nextBaselineValue, nextRecoveredAt, nextHealthyWindowCount,
                nextLastObservedWindow, nextPolicyReference, version);
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
