package org.zmy.observabilityplatform.audit.domain.model;

public enum AuditTargetType {
    INCIDENT,
    DIAGNOSIS,
    ANOMALY_POLICY,
    DEAD_LETTER,
    LOG_BATCH,
    HTTP_REQUEST
}
