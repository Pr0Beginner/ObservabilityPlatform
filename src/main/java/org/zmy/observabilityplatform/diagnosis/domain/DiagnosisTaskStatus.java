package org.zmy.observabilityplatform.diagnosis.domain;

public enum DiagnosisTaskStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    TIMEOUT,
    CANCELLED
}
