package org.zmy.observabilityplatform.logging.application.query;

import java.time.Instant;

public record LogSearchQuery(
        Instant from,
        Instant to,
        String service,
        String environment,
        String level,
        String traceId,
        String keyword,
        String fingerprint,
        int size) {
}
