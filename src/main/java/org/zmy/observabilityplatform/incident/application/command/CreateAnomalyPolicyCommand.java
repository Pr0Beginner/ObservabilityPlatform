package org.zmy.observabilityplatform.incident.application.command;

import lombok.Value;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyDetectionSettings;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicyScope;

@Value
public class CreateAnomalyPolicyCommand {
    String name;
    AnomalyPolicyScope scope;
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

    public AnomalyDetectionSettings settings() {
        return new AnomalyDetectionSettings(errorThreshold, minimumRequests, minimumErrorCodeCount,
                requestSpikeRatio, requestDropRatio, failureRateThreshold, errorCodeRateThreshold,
                baselineMultiplier, comparisonPeriodMinutes, recoveryWindows);
    }
}
