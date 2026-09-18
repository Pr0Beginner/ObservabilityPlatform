package org.zmy.observabilityplatform.logging.application.command;

import java.time.Instant;
import java.util.Map;

public record IngestLogItemCommand(
        Instant timestamp,
        String content,
        String format,
        String traceId,
        Map<String, Object> attributes) {
}
