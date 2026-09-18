package org.zmy.observabilityplatform.diagnosis.interfaces.rest.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.zmy.observabilityplatform.diagnosis.application.dto.DiagnosisReportView;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisReportResponse {
    private String id;
    private String taskId;
    private int version;
    private String rootCause;
    private double confidence;
    private List<String> evidence;
    private List<String> recommendations;
    private List<String> toolCalls;
    private Instant generatedAt;

    public static DiagnosisReportResponse from(DiagnosisReportView view) {
        return new DiagnosisReportResponse(view.getId(), view.getTaskId(), view.getVersion(), view.getRootCause(),
                view.getConfidence(), view.getEvidence(), view.getRecommendations(), view.getToolCalls(),
                view.getGeneratedAt());
    }
}
