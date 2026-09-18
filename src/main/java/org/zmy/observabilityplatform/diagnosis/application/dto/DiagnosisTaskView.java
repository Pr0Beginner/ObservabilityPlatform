package org.zmy.observabilityplatform.diagnosis.application.dto;

import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisTask;

import java.time.Instant;

public record DiagnosisTaskView(
        String id,
        String incidentId,
        int version,
        String status,
        Instant createdAt,
        Instant updatedAt,
        String failureReason) {

    public static DiagnosisTaskView from(DiagnosisTask task) {
        return new DiagnosisTaskView(task.id(), task.incidentId(), task.version(), task.status().name(),
                task.createdAt(), task.updatedAt(), task.failureReason());
    }
}
