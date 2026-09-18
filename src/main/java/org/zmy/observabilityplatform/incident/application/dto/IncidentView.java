package org.zmy.observabilityplatform.incident.application.dto;

import org.zmy.observabilityplatform.incident.domain.model.Incident;

import java.time.Instant;

public record IncidentView(
        String id,
        String dedupKey,
        String title,
        String service,
        String environment,
        String fingerprint,
        String severity,
        String status,
        Instant startedAt,
        Instant updatedAt,
        long errorCount,
        String assignee,
        String resolution) {

    public static IncidentView from(Incident incident) {
        return new IncidentView(incident.id(), incident.dedupKey(), incident.title(), incident.service(),
                incident.environment(), incident.fingerprint(), incident.severity().name(), incident.status().name(),
                incident.startedAt(), incident.updatedAt(), incident.errorCount(), incident.assignee(),
                incident.resolution());
    }
}
