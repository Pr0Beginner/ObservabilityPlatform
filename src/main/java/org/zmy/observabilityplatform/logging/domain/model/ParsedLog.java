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
    private final Map<String, Object> attributes;

    private ParsedLog(Instant timestamp, String level, String message, String traceId,
                      Map<String, Object> attributes) {
        this.timestamp = timestamp;
        this.level = level;
        this.message = message == null ? "" : message;
        this.traceId = traceId;
        this.attributes = Collections.unmodifiableMap(
                new LinkedHashMap<>(attributes == null ? Map.of() : attributes));
    }

    public static ParsedLog parsed(Instant timestamp, String level, String message, String traceId,
                                   Map<String, Object> attributes) {
        return new ParsedLog(timestamp, level, message, traceId, attributes);
    }

    public static ParsedLog unparsed(RawLogRecord record, Map<String, Object> attributes) {
        return new ParsedLog(record.getTimestamp(), "UNKNOWN", record.getContent(), record.getTraceId(), attributes);
    }
}
