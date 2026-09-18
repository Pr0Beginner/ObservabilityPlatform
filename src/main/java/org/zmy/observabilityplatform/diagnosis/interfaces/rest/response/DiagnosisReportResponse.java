package org.zmy.observabilityplatform.diagnosis.interfaces.rest.response;

import org.zmy.observabilityplatform.diagnosis.application.dto.DiagnosisReportView;

import java.time.Instant;
import java.util.List;

public record DiagnosisReportResponse(
        String id,
        String taskId,
        int version,
        String rootCause,
        double confidence,
        List<String> evidence,
        List<String> recommendations,
        List<String> toolCalls,
        Instant generatedAt) {

    public static DiagnosisReportResponse from(DiagnosisReportView view) {
        return new DiagnosisReportResponse(view.id(), view.taskId(), view.version(), view.rootCause(),
                view.confidence(), view.evidence(), view.recommendations(), view.toolCalls(), view.generatedAt());
    }
}
