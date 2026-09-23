package org.zmy.observabilityplatform.diagnosis.domain.exception;

import org.zmy.observabilityplatform.shared.exception.BusinessConflictException;

public final class DiagnosisStateConflictException extends BusinessConflictException {
    public DiagnosisStateConflictException(String taskId) {
        super("Diagnosis task state changed concurrently: " + taskId);
    }
}
