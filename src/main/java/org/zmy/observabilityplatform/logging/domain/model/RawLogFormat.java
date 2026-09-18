package org.zmy.observabilityplatform.logging.domain.model;

import java.util.Locale;

public enum RawLogFormat {
    AUTO,
    JSON,
    TEXT;

    public static RawLogFormat from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("format must not be blank");
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalidFormat) {
            throw new IllegalArgumentException("Unsupported log format: " + value, invalidFormat);
        }
    }
}
