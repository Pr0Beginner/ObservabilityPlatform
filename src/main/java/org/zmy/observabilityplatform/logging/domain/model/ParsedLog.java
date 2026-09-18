package org.zmy.observabilityplatform.logging.domain.model;

import java.time.Instant;
import java.util.Map;

public record ParsedLog(
        Instant timestamp,
        String level,
        String message,
        String traceId,
        Map<String, Object> attributes) {
}
