package org.zmy.observabilityplatform.logging.domain.service;

import java.util.Map;

/** Protects log content while preserving the structure of supported formats. */
public interface SensitiveDataProtector {
    String protect(String value);

    Map<String, Object> protectAttributes(Map<String, Object> attributes);
}
