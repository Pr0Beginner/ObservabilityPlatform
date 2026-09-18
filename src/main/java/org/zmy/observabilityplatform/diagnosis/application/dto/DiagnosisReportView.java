package org.zmy.observabilityplatform.diagnosis.application.dto;

import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisReport;

import java.time.Instant;
import java.util.List;

public record DiagnosisReportView(
        String id,
        String taskId,
        int version,
        String rootCause,
        double confidence,
        List<String> evidence,
        List<String> recommendations,
        List<String> toolCalls,
        Instant generatedAt) {

    public static DiagnosisReportView from(DiagnosisReport report) {
        return new DiagnosisReportView(report.id(), report.taskId(), report.version(), report.rootCause(),
                report.confidence(), report.evidence(), report.recommendations(), report.toolCalls(),
                report.generatedAt());
    }
}
