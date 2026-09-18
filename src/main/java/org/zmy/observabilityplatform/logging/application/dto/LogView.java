package org.zmy.observabilityplatform.logging.application.dto;

import lombok.Value;
import org.zmy.observabilityplatform.logging.domain.model.LogEntry;

import java.time.Instant;
import java.util.Map;

@Value
public class LogView {
    String id;
    String batchId;
    Instant timestamp;
    Instant receivedAt;
    String service;
    String environment;
    String level;
    String traceId;
    String rawMessage;
    String message;
    String fingerprint;
    Map<String, Object> attributes;

    public static LogView from(LogEntry entry) {
        return new LogView(entry.getId(), entry.getBatchId(), entry.getTimestamp(), entry.getReceivedAt(),
                entry.getService(), entry.getEnvironment(), entry.getLevel(), entry.getTraceId(),
                entry.getRawMessage(), entry.getMessage(), entry.getFingerprint(), entry.getAttributes());
    }
}
