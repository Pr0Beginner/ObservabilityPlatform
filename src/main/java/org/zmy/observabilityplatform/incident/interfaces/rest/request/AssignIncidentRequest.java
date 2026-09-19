package org.zmy.observabilityplatform.incident.interfaces.rest.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AssignIncidentRequest {
    @NotBlank
    private String assignee;
}
