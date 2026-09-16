package org.zmy.observabilityplatform.logging.domain;

import java.time.Instant;
import java.util.Map;

public record LogEntry(
        String id,
        String batchId,
        Instant timestamp,
        Instant receivedAt,
        String service,
        String environment,
        String level,
        String traceId,
        String rawMessage,
        String message,
        String fingerprint,
        Map<String, Object> attributes) {
}
