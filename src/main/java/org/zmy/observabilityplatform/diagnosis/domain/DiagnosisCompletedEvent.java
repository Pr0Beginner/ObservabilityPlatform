package org.zmy.observabilityplatform.diagnosis.domain;

import java.time.Instant;
import java.util.List;

public record DiagnosisCompletedEvent(
        String eventId,
        String taskId,
        String incidentId,
        int version,
        String rootCause,
        double confidence,
        List<String> evidence,
        List<String> recommendations,
        List<String> toolCalls,
        Instant completedAt,
        String error) {
}
