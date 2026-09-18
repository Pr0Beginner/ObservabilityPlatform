package org.zmy.observabilityplatform.diagnosis.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;

@Value
@Builder
@Jacksonized
@AllArgsConstructor
public class DiagnosisCompletedEvent {
    String eventId;
    String taskId;
    String incidentId;
    int version;
    String rootCause;
    double confidence;
    List<String> evidence;
    List<String> recommendations;
    List<String> toolCalls;
    Instant completedAt;
    String error;
}
