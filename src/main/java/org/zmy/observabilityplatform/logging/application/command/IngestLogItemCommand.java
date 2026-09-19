package org.zmy.observabilityplatform.logging.application.command;

import lombok.Value;

import java.time.Instant;
import java.util.Map;

@Value
public class IngestLogItemCommand {
    Instant timestamp;
    String content;
    String format;
    String traceId;
    String spanId;
    String parentSpanId;
    String requestId;
    String operation;
    String spanKind;
    Integer statusCode;
    Boolean success;
    String errorCode;
    Long durationMs;
    Map<String, Object> attributes;
}
