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
    String keyword;
    String fingerprint;
    int size;
}
