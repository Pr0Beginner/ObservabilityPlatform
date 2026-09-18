package org.zmy.observabilityplatform.diagnosis.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

@Value
@Builder
@Jacksonized
@AllArgsConstructor
public class DiagnosisRequestedEvent {
    String eventId;
    String taskId;
    String incidentId;
    int version;
    Instant requestedAt;
}
