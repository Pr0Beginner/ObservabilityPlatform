package org.zmy.observabilityplatform.incident.domain.exception;

import org.zmy.observabilityplatform.shared.exception.BusinessConflictException;

public class AnomalyPolicyConflictException extends BusinessConflictException {
    public AnomalyPolicyConflictException(String message) {
        super(message);
    }

    public static AnomalyPolicyConflictException duplicateScope() {
        return new AnomalyPolicyConflictException("An anomaly policy already exists for this scope");
    }

    public static AnomalyPolicyConflictException staleVersion(String policyId, long expectedVersion) {
        return new AnomalyPolicyConflictException(
                "Anomaly policy was modified concurrently: id=" + policyId + ", expectedVersion=" + expectedVersion);
    }
}
