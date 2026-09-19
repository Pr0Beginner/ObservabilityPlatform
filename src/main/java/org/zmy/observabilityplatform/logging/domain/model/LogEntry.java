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
    private final String spanId;
    private final String parentSpanId;
    private final String requestId;
    private final String operation;
    private final String spanKind;
    private final Integer statusCode;
    private final Boolean success;
    private final String errorCode;
    private final Long durationMs;
    private final String rawMessage;
    private final String message;
    private final String fingerprint;
    private final Map<String, Object> attributes;

    private LogEntry(String id, String batchId, Instant timestamp, Instant receivedAt, String service,
                     String environment, String level, String traceId, String spanId, String parentSpanId,
                     String requestId, String operation, String spanKind, Integer statusCode, Boolean success,
                     String errorCode, Long durationMs, String rawMessage, String message, String fingerprint,
                     Map<String, Object> attributes) {
        this.id = requireText(id, "id");
        this.batchId = requireText(batchId, "batchId");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        this.service = requireText(service, "service", 120);
        this.environment = requireText(environment, "environment", 80);
        this.level = requireText(level, "level");
        this.traceId = optionalText(traceId, "traceId", 64);
        this.spanId = optionalText(spanId, "spanId", 32);
        this.parentSpanId = optionalText(parentSpanId, "parentSpanId", 32);
        this.requestId = optionalText(requestId, "requestId", 255);
        this.operation = optionalText(operation, "operation", 255);
        this.spanKind = optionalText(spanKind, "spanKind", 20);
        this.statusCode = statusCode;
        this.success = success;
        this.errorCode = optionalText(errorCode, "errorCode", 255);
        if (durationMs != null && durationMs < 0) {
            throw new IllegalArgumentException("durationMs must not be negative");
        }
        this.durationMs = durationMs;
        if (this.parentSpanId != null && this.spanId == null) {
            throw new IllegalArgumentException("spanId is required when parentSpanId is present");
        }
        this.rawMessage = rawMessage == null ? "" : rawMessage;
        this.message = message == null ? "" : message;
        this.fingerprint = requireText(fingerprint, "fingerprint", 64);
        this.attributes = Collections.unmodifiableMap(
                new LinkedHashMap<>(attributes == null ? Map.of() : attributes));
    }

    public static LogEntry create(String id, String batchId, Instant timestamp, Instant receivedAt,
                                  String service, String environment, String level, String traceId,
                                  String rawMessage, String message, String fingerprint,
                                  Map<String, Object> attributes) {
        return new LogEntry(id, batchId, timestamp, receivedAt, service, environment, level, traceId,
                null, null, null, null, null, null, null, null, null,
                rawMessage, message, fingerprint, attributes);
    }

    public static LogEntry create(String id, String batchId, Instant timestamp, Instant receivedAt,
                                  String service, String environment, String level, String traceId,
                                  String spanId, String parentSpanId, String requestId, String operation,
                                  String spanKind, Integer statusCode, Boolean success, String errorCode,
                                  Long durationMs, String rawMessage, String message, String fingerprint,
                                  Map<String, Object> attributes) {
        return new LogEntry(id, batchId, timestamp, receivedAt, service, environment, level, traceId,
                spanId, parentSpanId, requestId, operation, spanKind, statusCode, success, errorCode,
                durationMs, rawMessage, message, fingerprint, attributes);
    }

    public boolean isError() {
        return "ERROR".equals(level) || "FATAL".equals(level);
    }

    private static String requireText(String value, String field) {
        return requireText(value, field, Integer.MAX_VALUE);
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String optionalText(String value, String field, int maxLength) {
        String normalized = blankToNull(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }
}
