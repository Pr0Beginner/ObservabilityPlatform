package org.zmy.observabilityplatform.incident.interfaces.rest.response;

import org.zmy.observabilityplatform.incident.application.dto.IncidentView;

import java.time.Instant;

public record IncidentResponse(
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

    public static IncidentResponse from(IncidentView view) {
        return new IncidentResponse(view.id(), view.dedupKey(), view.title(), view.service(), view.environment(),
                view.fingerprint(), view.severity(), view.status(), view.startedAt(), view.updatedAt(),
                view.errorCount(), view.assignee(), view.resolution());
    }
}
