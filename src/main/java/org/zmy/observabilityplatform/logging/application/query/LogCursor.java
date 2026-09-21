package org.zmy.observabilityplatform.logging.application.query;

import lombok.Value;

@Value
public class LogCursor {
    long timestampEpochMillis;
    String id;

    public LogCursor(long timestampEpochMillis, String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("cursor id must not be blank");
        }
        this.timestampEpochMillis = timestampEpochMillis;
        this.id = id;
    }
}
