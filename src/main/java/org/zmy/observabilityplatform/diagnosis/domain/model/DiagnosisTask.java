package org.zmy.observabilityplatform.diagnosis.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;

@Getter
@ToString
@EqualsAndHashCode
public final class DiagnosisTask {
    private final String id;
    private final String incidentId;
    private final int version;
    private final DiagnosisTaskStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String failureReason;

    private DiagnosisTask(String id, String incidentId, int version, DiagnosisTaskStatus status,
                          Instant createdAt, Instant updatedAt, String failureReason) {
        this.id = requireText(id, "id");
        this.incidentId = requireText(incidentId, "incidentId");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        this.version = version;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        if (status == DiagnosisTaskStatus.FAILED && (failureReason == null || failureReason.isBlank())) {
            throw new IllegalArgumentException("failureReason is required for a failed diagnosis task");
        }
        if (status != DiagnosisTaskStatus.FAILED && failureReason != null && !failureReason.isBlank()) {
            throw new IllegalArgumentException("failureReason is only allowed for a failed diagnosis task");
        }
        this.failureReason = failureReason == null || failureReason.isBlank() ? null : failureReason;
    }

    public static DiagnosisTask request(String id, String incidentId, int version, Instant requestedAt) {
        return new DiagnosisTask(id, incidentId, version, DiagnosisTaskStatus.PENDING,
                requestedAt, requestedAt, null);
    }

    public static DiagnosisTask restore(String id, String incidentId, int version, DiagnosisTaskStatus status,
                                        Instant createdAt, Instant updatedAt, String failureReason) {
        return new DiagnosisTask(id, incidentId, version, status, createdAt, updatedAt, failureReason);
    }

    public boolean isActive() {
        return status == DiagnosisTaskStatus.PENDING || status == DiagnosisTaskStatus.RUNNING;
    }

    public DiagnosisTask start(Instant changedAt) {
        requireStatus(DiagnosisTaskStatus.PENDING);
        return copy(DiagnosisTaskStatus.RUNNING, changedAt, null);
    }

    public DiagnosisTask complete(DiagnosisReport report, Instant changedAt) {
        requireActive();
        Objects.requireNonNull(report, "report must not be null");
        if (!id.equals(report.getTaskId()) || version != report.getVersion()) {
            throw new IllegalArgumentException("The diagnosis report does not belong to this task version");
        }
        return copy(DiagnosisTaskStatus.SUCCEEDED, changedAt, null);
    }

    public DiagnosisTask fail(String reason, Instant changedAt) {
        requireActive();
        return copy(DiagnosisTaskStatus.FAILED, changedAt, requireText(reason, "failureReason"));
    }

    public DiagnosisTask timeout(Instant changedAt) {
        requireActive();
        return copy(DiagnosisTaskStatus.TIMEOUT, changedAt, null);
    }

    public DiagnosisTask cancel(Instant changedAt) {
        requireActive();
        return copy(DiagnosisTaskStatus.CANCELLED, changedAt, null);
    }

    private DiagnosisTask copy(DiagnosisTaskStatus newStatus, Instant changedAt, String reason) {
        Objects.requireNonNull(changedAt, "changedAt must not be null");
        if (changedAt.isBefore(updatedAt)) {
            throw new IllegalArgumentException("changedAt must not be before the current updatedAt");
        }
        return new DiagnosisTask(id, incidentId, version, newStatus, createdAt, changedAt, reason);
    }

    private void requireActive() {
        if (!isActive()) {
            throw new IllegalStateException("Diagnosis task is already terminal: " + status);
        }
    }

    private void requireStatus(DiagnosisTaskStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected diagnosis status " + expected + " but was " + status);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
