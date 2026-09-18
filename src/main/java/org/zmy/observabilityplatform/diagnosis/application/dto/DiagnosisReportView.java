package org.zmy.observabilityplatform.diagnosis.application.dto;

import lombok.Value;
import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisReport;

import java.time.Instant;
import java.util.List;

@Value
public class DiagnosisReportView {
    String id;
    String taskId;
    int version;
    String rootCause;
    double confidence;
    List<String> evidence;
    List<String> recommendations;
    List<String> toolCalls;
    Instant generatedAt;

    public static DiagnosisReportView from(DiagnosisReport report) {
        return new DiagnosisReportView(report.getId(), report.getTaskId(), report.getVersion(),
                report.getRootCause(), report.getConfidence(), report.getEvidence(), report.getRecommendations(),
                report.getToolCalls(), report.getGeneratedAt());
    }
}
