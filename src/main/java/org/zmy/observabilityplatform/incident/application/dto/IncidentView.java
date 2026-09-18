package org.zmy.observabilityplatform.incident.application.dto;

import lombok.Value;
import org.zmy.observabilityplatform.incident.domain.model.Incident;

import java.time.Instant;

@Value
public class IncidentView {
    String id;
    String dedupKey;
    String title;
    String service;
    String environment;
    String fingerprint;
    String severity;
    String status;
    Instant startedAt;
    Instant updatedAt;
    long errorCount;
    String assignee;
    String resolution;

    public static IncidentView from(Incident incident) {
        return new IncidentView(incident.getId(), incident.getDedupKey(), incident.getTitle(), incident.getService(),
                incident.getEnvironment(), incident.getFingerprint(), incident.getSeverity().name(),
                incident.getStatus().name(), incident.getStartedAt(), incident.getUpdatedAt(),
                incident.getErrorCount(), incident.getAssignee(), incident.getResolution());
    }
}
