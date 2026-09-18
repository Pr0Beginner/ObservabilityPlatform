package org.zmy.observabilityplatform.incident.application.command;

import java.time.Instant;

public record ObservedLogCommand(
        Instant timestamp,
        String service,
        String environment,
        String level,
        String fingerprint) {
}
