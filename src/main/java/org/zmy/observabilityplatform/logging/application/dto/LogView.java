package org.zmy.observabilityplatform.logging.application.dto;

import org.zmy.observabilityplatform.logging.domain.model.LogEntry;

import java.time.Instant;
import java.util.Map;

public record LogView(
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

    public static LogView from(LogEntry entry) {
        return new LogView(entry.id(), entry.batchId(), entry.timestamp(), entry.receivedAt(),
                entry.service(), entry.environment(), entry.level(), entry.traceId(), entry.rawMessage(),
                entry.message(), entry.fingerprint(), entry.attributes());
    }
}
