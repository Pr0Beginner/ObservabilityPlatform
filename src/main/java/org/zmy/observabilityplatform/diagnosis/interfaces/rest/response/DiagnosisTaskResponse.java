package org.zmy.observabilityplatform.diagnosis.interfaces.rest.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.zmy.observabilityplatform.diagnosis.application.dto.DiagnosisTaskView;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisTaskResponse {
    private String id;
    private String incidentId;
    private int version;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private String failureReason;

    public static DiagnosisTaskResponse from(DiagnosisTaskView view) {
        return new DiagnosisTaskResponse(view.getId(), view.getIncidentId(), view.getVersion(), view.getStatus(),
                view.getCreatedAt(), view.getUpdatedAt(), view.getFailureReason());
    }
}
