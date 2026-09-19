package org.zmy.observabilityplatform.incident.application.dto;

import lombok.Value;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyDetectionSettings;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicy;

import java.time.Instant;

@Value
public class AnomalyPolicyView {
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

    public static AnomalyPolicyView from(AnomalyPolicy policy) {
        AnomalyDetectionSettings settings = policy.getSettings();
        return new AnomalyPolicyView(policy.getId(), policy.getName(), policy.getScope().name(),
                policy.getService(), policy.getEnvironment(), policy.getOperation(), policy.isEnabled(),
                settings.getErrorThreshold(), settings.getMinimumRequests(), settings.getMinimumErrorCodeCount(),
                settings.getRequestSpikeRatio(), settings.getRequestDropRatio(),
                settings.getFailureRateThreshold(), settings.getErrorCodeRateThreshold(),
                settings.getBaselineMultiplier(), settings.getComparisonPeriodMinutes(),
                settings.getRecoveryWindows(), policy.getVersion(), policy.getCreatedAt(), policy.getUpdatedAt());
    }
}
