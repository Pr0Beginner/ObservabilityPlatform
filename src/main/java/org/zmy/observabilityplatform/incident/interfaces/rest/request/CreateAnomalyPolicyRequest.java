package org.zmy.observabilityplatform.incident.interfaces.rest.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.zmy.observabilityplatform.incident.application.command.CreateAnomalyPolicyCommand;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicyScope;

@Data
@NoArgsConstructor
public class CreateAnomalyPolicyRequest {
    @NotBlank
    @Size(max = 120)
    private String name;
    @NotNull
    private AnomalyPolicyScope scope;
    @Size(max = 120)
    private String service;
    @Size(max = 80)
    private String environment;
    @Size(max = 255)
    private String operation;
    @NotNull
    private Boolean enabled;
    @NotNull
    @Positive
    private Integer errorThreshold;
    @NotNull
    @Positive
    private Integer minimumRequests;
    @NotNull
    @Positive
    private Integer minimumErrorCodeCount;
    @NotNull
    @DecimalMin(value = "1.0", inclusive = false)
    private Double requestSpikeRatio;
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax(value = "1.0", inclusive = false)
    private Double requestDropRatio;
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @DecimalMax("1.0")
    private Double failureRateThreshold;
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @DecimalMax("1.0")
    private Double errorCodeRateThreshold;
    @NotNull
    @DecimalMin(value = "1.0", inclusive = false)
    private Double baselineMultiplier;
    @NotNull
    @Positive
    private Integer comparisonPeriodMinutes;
    @NotNull
    @Positive
    private Integer recoveryWindows;

    public CreateAnomalyPolicyCommand toCommand() {
        return new CreateAnomalyPolicyCommand(name, scope, service, environment, operation, enabled,
                errorThreshold, minimumRequests, minimumErrorCodeCount, requestSpikeRatio,
                requestDropRatio, failureRateThreshold, errorCodeRateThreshold, baselineMultiplier,
                comparisonPeriodMinutes, recoveryWindows);
    }
}
