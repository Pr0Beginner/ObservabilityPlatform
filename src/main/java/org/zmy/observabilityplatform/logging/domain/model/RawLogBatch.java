package org.zmy.observabilityplatform.logging.domain.model;

import java.time.Instant;
import java.util.List;

public record RawLogBatch(
        String batchId,
        String service,
        String environment,
        Instant receivedAt,
        List<RawLogRecord> logs) {
}
