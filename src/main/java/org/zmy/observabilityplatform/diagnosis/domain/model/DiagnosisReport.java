package org.zmy.observabilityplatform.diagnosis.domain.model;

import java.time.Instant;
import java.util.List;

public record DiagnosisReport(
        String id,
        String taskId,
        int version,
        String rootCause,
        double confidence,
        List<String> evidence,
        List<String> recommendations,
        List<String> toolCalls,
        Instant generatedAt) {
}
