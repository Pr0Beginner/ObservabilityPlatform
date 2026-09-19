package org.zmy.observabilityplatform.incident.application.command;

import lombok.Value;

import java.time.Instant;

@Value
public class ObservedLogCommand {
    Instant timestamp;
    String service;
    String environment;
    String level;
    String fingerprint;
    String traceId;
    String requestId;
    String operation;
    String spanKind;
    Integer statusCode;
    Boolean success;
    String errorCode;
    Long durationMs;
}
