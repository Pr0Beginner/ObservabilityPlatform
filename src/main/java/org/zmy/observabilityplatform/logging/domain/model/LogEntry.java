package org.zmy.observabilityplatform.logging.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Getter
@ToString
@EqualsAndHashCode
@Builder
@Jacksonized
public final class LogEntry {
    private final String id;
    private final String batchId;
    private final Instant timestamp;
    private final Instant receivedAt;
    private final String service;
    private final String environment;
    private final String level;
    private final String traceId;
    private final String rawMessage;
    private final String message;
    private final String fingerprint;
    private final Map<String, Object> attributes;

    private LogEntry(String id, String batchId, Instant timestamp, Instant receivedAt, String service,
                     String environment, String level, String traceId, String rawMessage, String message,
                     String fingerprint, Map<String, Object> attributes) {
        this.id = requireText(id, "id");
        this.batchId = requireText(batchId, "batchId");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        this.service = requireText(service, "service");
        this.environment = requireText(environment, "environment");
        this.level = requireText(level, "level");
        this.traceId = traceId;
        this.rawMessage = rawMessage == null ? "" : rawMessage;
        this.message = message == null ? "" : message;
        this.fingerprint = requireText(fingerprint, "fingerprint");
        this.attributes = Collections.unmodifiableMap(
                new LinkedHashMap<>(attributes == null ? Map.of() : attributes));
    }

    public static LogEntry create(String id, String batchId, Instant timestamp, Instant receivedAt,
                                  String service, String environment, String level, String traceId,
                                  String rawMessage, String message, String fingerprint,
                                  Map<String, Object> attributes) {
        return new LogEntry(id, batchId, timestamp, receivedAt, service, environment, level, traceId,
                rawMessage, message, fingerprint, attributes);
    }

    public boolean isError() {
        return "ERROR".equals(level) || "FATAL".equals(level);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
