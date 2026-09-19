package org.zmy.observabilityplatform.incident.domain.model;

import lombok.Value;

import java.time.Instant;

@Value
public class IncidentTraceLink {
    String incidentId;
    String traceId;
    Instant linkedAt;

    public IncidentTraceLink(String incidentId, String traceId, Instant linkedAt) {
        if (incidentId == null || incidentId.isBlank()) {
            throw new IllegalArgumentException("incidentId must not be blank");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        this.incidentId = incidentId;
        this.traceId = traceId;
        this.linkedAt = java.util.Objects.requireNonNull(linkedAt, "linkedAt must not be null");
    }
}
