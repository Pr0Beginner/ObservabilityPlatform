package org.zmy.observabilityplatform.logging.interfaces.rest.request;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.Map;

public record LogItemRequest(
        Instant timestamp,
        @NotBlank String content,
        @NotBlank String format,
        String traceId,
        Map<String, Object> attributes) {
}
