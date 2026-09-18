package org.zmy.observabilityplatform.diagnosis.domain.model;

public enum DiagnosisTaskStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    TIMEOUT,
    CANCELLED
}
