package org.zmy.observabilityplatform.logging.application.query;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class LogCursorCodec {
    private static final int MAX_CURSOR_LENGTH = 1_024;

    public String encode(LogCursor cursor) {
        String payload = cursor.getTimestampEpochMillis() + ":" + cursor.getId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public LogCursor decode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > MAX_CURSOR_LENGTH) {
            throw invalidCursor(null);
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            int separator = payload.indexOf(':');
            if (separator < 1 || separator == payload.length() - 1) {
                throw new IllegalArgumentException("Malformed cursor payload");
            }
            long timestamp = Long.parseLong(payload.substring(0, separator));
            return new LogCursor(timestamp, payload.substring(separator + 1));
        } catch (IllegalArgumentException exception) {
            throw invalidCursor(exception);
        }
    }

    private IllegalArgumentException invalidCursor(Throwable cause) {
        return new IllegalArgumentException("Invalid log cursor", cause);
    }
}
