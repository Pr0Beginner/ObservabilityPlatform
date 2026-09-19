package org.zmy.observabilityplatform.incident.domain.exception;

public class IncidentVersionConflictException extends RuntimeException {
    public IncidentVersionConflictException(String incidentId, long expectedVersion) {
        super("Incident was modified concurrently: id=" + incidentId + ", expectedVersion=" + expectedVersion);
    }
}
