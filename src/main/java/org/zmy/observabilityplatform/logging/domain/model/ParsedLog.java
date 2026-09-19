package org.zmy.observabilityplatform.logging.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@ToString
@EqualsAndHashCode
public final class ParsedLog {
    private final Instant timestamp;
    private final String level;
    private final String message;
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

    private ParsedLog(Instant timestamp, String level, String message, String traceId, String spanId,
                      String parentSpanId, String requestId, String operation, String spanKind,
                      Integer statusCode, Boolean success, String errorCode, Long durationMs,
                      Map<String, Object> attributes) {
        this.timestamp = timestamp;
        this.level = level;
        this.message = message == null ? "" : message;
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.requestId = requestId;
        this.operation = operation;
        this.spanKind = spanKind;
        this.statusCode = statusCode;
        this.success = success;
        this.errorCode = errorCode;
        this.durationMs = durationMs;
        this.attributes = Collections.unmodifiableMap(
                new LinkedHashMap<>(attributes == null ? Map.of() : attributes));
    }

    public static ParsedLog parsed(Instant timestamp, String level, String message, String traceId, String spanId,
                                   String parentSpanId, String requestId, String operation, String spanKind,
                                   Integer statusCode, Boolean success, String errorCode, Long durationMs,
                                   Map<String, Object> attributes) {
        return new ParsedLog(timestamp, level, message, traceId, spanId, parentSpanId, requestId,
                operation, spanKind, statusCode, success, errorCode, durationMs, attributes);
    }

    public static ParsedLog unparsed(RawLogRecord record, Map<String, Object> attributes) {
        return new ParsedLog(record.getTimestamp(), "UNKNOWN", record.getContent(), record.getTraceId(),
                record.getSpanId(), record.getParentSpanId(), record.getRequestId(), record.getOperation(),
                record.getSpanKind(), record.getStatusCode(), record.getSuccess(), record.getErrorCode(),
                record.getDurationMs(), attributes);
    }
}
