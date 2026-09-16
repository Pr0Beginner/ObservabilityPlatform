package org.zmy.observabilityplatform.diagnosis.domain;

import java.time.Instant;

public record DiagnosisTask(
        String id,
        String incidentId,
        int version,
        DiagnosisTaskStatus status,
        Instant createdAt,
        Instant updatedAt,
        String failureReason) {

    public DiagnosisTask withStatus(DiagnosisTaskStatus newStatus, String reason) {
        return new DiagnosisTask(id, incidentId, version, newStatus, createdAt, Instant.now(), reason);
    }
}
