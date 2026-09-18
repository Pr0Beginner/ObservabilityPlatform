package org.zmy.observabilityplatform.logging.domain.model;

import java.time.Instant;
import java.util.Map;

public record RawLogRecord(
        String id,
        Instant timestamp,
        String content,
        RawLogFormat format,
        String traceId,
        Map<String, Object> attributes) {
}
