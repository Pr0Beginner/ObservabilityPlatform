package org.zmy.observabilityplatform.incident.domain.model;

public enum IncidentType {
    REPEATED_ERROR,
    REQUEST_VOLUME_SPIKE,
    REQUEST_VOLUME_DROP,
    NO_TRAFFIC,
    ERROR_CODE_COUNT_SPIKE,
    ERROR_CODE_RATE_SPIKE,
    FAILURE_RATE_SPIKE
}
