package org.zmy.observabilityplatform.diagnosis.domain;

import java.time.Instant;

public record DiagnosisRequestedEvent(
        String eventId,
        String taskId,
        String incidentId,
        int version,
        Instant requestedAt) {
}
