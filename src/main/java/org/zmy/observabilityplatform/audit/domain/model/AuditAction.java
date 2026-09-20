package org.zmy.observabilityplatform.audit.domain.model;

public enum AuditAction {
    INCIDENT_ASSIGN,
    INCIDENT_TRANSITION,
    DIAGNOSIS_CREATE,
    DIAGNOSIS_CANCEL,
    DIAGNOSIS_RETRY,
    ANOMALY_POLICY_CREATE,
    ANOMALY_POLICY_UPDATE,
    DEAD_LETTER_REPLAY,
    LOG_INGEST,
    ACCESS_REQUEST
}
