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
    Map<String, Object> attributes;
}
