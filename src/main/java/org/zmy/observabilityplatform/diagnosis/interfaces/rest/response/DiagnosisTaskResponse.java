package org.zmy.observabilityplatform.diagnosis.interfaces.rest.response;

import org.zmy.observabilityplatform.diagnosis.application.dto.DiagnosisTaskView;

import java.time.Instant;

public record DiagnosisTaskResponse(
        String id,
        String incidentId,
        int version,
        String status,
        Instant createdAt,
        Instant updatedAt,
        String failureReason) {

    public static DiagnosisTaskResponse from(DiagnosisTaskView view) {
        return new DiagnosisTaskResponse(view.id(), view.incidentId(), view.version(), view.status(), view.createdAt(),
                view.updatedAt(), view.failureReason());
    }
}
