package org.zmy.observabilityplatform.logging.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Getter
@ToString
@EqualsAndHashCode
@Builder
@Jacksonized
public final class RawLogRecord {
    private final String id;
    private final Instant timestamp;
    private final String content;
    private final RawLogFormat format;
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
    private final Map<String, Object> attributes;

    private RawLogRecord(String id, Instant timestamp, String content, RawLogFormat format,
                         String traceId, String spanId, String parentSpanId, String requestId,
                         String operation, String spanKind, Integer statusCode, Boolean success,
                         String errorCode, Long durationMs, Map<String, Object> attributes) {
        this.id = requireText(id, "id");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.content = requireText(content, "content");
        this.format = Objects.requireNonNull(format, "format must not be null");
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
        this.attributes = Collections.unmodifiableMap(
                new LinkedHashMap<>(attributes == null ? Map.of() : attributes));
    }

    public static RawLogRecord capture(String batchId, int index, Instant timestamp, String content,
                                       String format, String traceId, String spanId, String parentSpanId,
                                       String requestId, String operation, String spanKind, Integer statusCode,
                                       Boolean success, String errorCode, Long durationMs,
                                       Map<String, Object> attributes) {
        String recordKey = requireText(batchId, "batchId") + ":" + index;
        String id = UUID.nameUUIDFromBytes(recordKey.getBytes(StandardCharsets.UTF_8)).toString();
        return new RawLogRecord(id, timestamp, content, RawLogFormat.from(format), traceId, spanId,
                parentSpanId, requestId, operation, spanKind, statusCode, success, errorCode, durationMs, attributes);
    }

    public static RawLogRecord capture(String batchId, int index, Instant timestamp, String content,
                                       String format, String traceId, Map<String, Object> attributes) {
        return capture(batchId, index, timestamp, content, format, traceId, null, null, null,
                null, null, null, null, null, null, attributes);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
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
