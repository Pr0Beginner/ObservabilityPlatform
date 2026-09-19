package org.zmy.observabilityplatform.incident.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
public final class AnomalyPolicyReference {
    private final String policyId;
    private final long policyVersion;

    public AnomalyPolicyReference(String policyId, long policyVersion) {
        if (policyId == null || policyId.isBlank()) {
            throw new IllegalArgumentException("policyId must not be blank");
        }
        if (policyVersion < 1) {
            throw new IllegalArgumentException("policyVersion must be positive");
        }
        this.policyId = policyId;
        this.policyVersion = policyVersion;
    }
}
