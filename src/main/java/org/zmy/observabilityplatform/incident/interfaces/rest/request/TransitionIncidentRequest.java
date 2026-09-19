package org.zmy.observabilityplatform.incident.interfaces.rest.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.zmy.observabilityplatform.incident.domain.model.IncidentStatus;

@Data
@NoArgsConstructor
public class TransitionIncidentRequest {
    @NotNull
    private IncidentStatus status;
    private String resolution;
}
