package org.zmy.observabilityplatform.logging.application.query;

import lombok.Value;

import java.time.Instant;

@Value
public class LogSearchQuery {
    Instant from;
    Instant to;
    String service;
    String environment;
    String level;
    String traceId;
    String spanId;
    String requestId;
    String keyword;
    String fingerprint;
    LogCursor cursor;
    int size;
}
