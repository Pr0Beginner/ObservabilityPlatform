package org.zmy.observabilityplatform.incident.domain.model;

import java.util.EnumSet;
import java.util.Set;

public enum IncidentStatus {
    OPEN,
    TRIAGING,
    DIAGNOSING,
    MITIGATING,
    RESOLVED,
    CLOSED;

    public boolean canTransitionTo(IncidentStatus target) {
        Set<IncidentStatus> allowed = switch (this) {
            case OPEN -> EnumSet.of(TRIAGING, DIAGNOSING, CLOSED);
            case TRIAGING -> EnumSet.of(DIAGNOSING, MITIGATING, RESOLVED);
            case DIAGNOSING -> EnumSet.of(TRIAGING, MITIGATING, RESOLVED);
            case MITIGATING -> EnumSet.of(RESOLVED, DIAGNOSING);
            case RESOLVED -> EnumSet.of(CLOSED, OPEN);
            case CLOSED -> EnumSet.of(OPEN);
        };
        return allowed.contains(target);
    }
}
