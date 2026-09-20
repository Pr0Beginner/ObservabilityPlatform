package org.zmy.observabilityplatform.audit.application.service;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AuditState {
    private AuditState() {
    }

    public static Map<String, Object> of(Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("Audit state requires key/value pairs");
        }
        Map<String, Object> state = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            Object key = entries[index];
            Object value = entries[index + 1];
            if (!(key instanceof String name) || name.isBlank()) {
                throw new IllegalArgumentException("Audit state key must be a non-blank string");
            }
            if (value != null) {
                state.put(name, value);
            }
        }
        return Map.copyOf(state);
    }
}
