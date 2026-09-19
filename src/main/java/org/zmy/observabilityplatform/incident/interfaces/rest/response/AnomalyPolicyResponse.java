package org.zmy.observabilityplatform.incident.interfaces.rest.response;

import lombok.Value;
import org.zmy.observabilityplatform.incident.application.dto.AnomalyPolicyView;

import java.time.Instant;

@Value
public class AnomalyPolicyResponse {
    String id;
    String name;
    String scope;
    String service;
    String environment;
    String operation;
    boolean enabled;
    int errorThreshold;
    int minimumRequests;
    int minimumErrorCodeCount;
    double requestSpikeRatio;
    double requestDropRatio;
    double failureRateThreshold;
    double errorCodeRateThreshold;
    double baselineMultiplier;
    int comparisonPeriodMinutes;
    int recoveryWindows;
    long version;
    Instant createdAt;
    Instant updatedAt;

    public static AnomalyPolicyResponse from(AnomalyPolicyView view) {
        return new AnomalyPolicyResponse(view.getId(), view.getName(), view.getScope(), view.getService(),
                view.getEnvironment(), view.getOperation(), view.isEnabled(), view.getErrorThreshold(),
                view.getMinimumRequests(), view.getMinimumErrorCodeCount(), view.getRequestSpikeRatio(),
                view.getRequestDropRatio(), view.getFailureRateThreshold(), view.getErrorCodeRateThreshold(),
                view.getBaselineMultiplier(), view.getComparisonPeriodMinutes(), view.getRecoveryWindows(),
                view.getVersion(), view.getCreatedAt(), view.getUpdatedAt());
    }
}
