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
    private final Map<String, Object> attributes;

    private RawLogRecord(String id, Instant timestamp, String content, RawLogFormat format,
                         String traceId, Map<String, Object> attributes) {
        this.id = requireText(id, "id");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.content = requireText(content, "content");
        this.format = Objects.requireNonNull(format, "format must not be null");
        this.traceId = blankToNull(traceId);
        this.attributes = Collections.unmodifiableMap(
                new LinkedHashMap<>(attributes == null ? Map.of() : attributes));
    }

    public static RawLogRecord capture(String batchId, int index, Instant timestamp, String content,
                                       String format, String traceId, Map<String, Object> attributes) {
        String recordKey = requireText(batchId, "batchId") + ":" + index;
        String id = UUID.nameUUIDFromBytes(recordKey.getBytes(StandardCharsets.UTF_8)).toString();
        return new RawLogRecord(id, timestamp, content, RawLogFormat.from(format), traceId, attributes);
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
}
