package org.zmy.observabilityplatform.incident.application.dto;

import lombok.Getter;
import org.zmy.observabilityplatform.incident.domain.model.Incident;

import java.util.Objects;

@Getter
public final class IncidentChange {
    private final Incident before;
    private final Incident after;

    public IncidentChange(Incident before, Incident after) {
        this.before = Objects.requireNonNull(before, "before must not be null");
        this.after = Objects.requireNonNull(after, "after must not be null");
    }
}
