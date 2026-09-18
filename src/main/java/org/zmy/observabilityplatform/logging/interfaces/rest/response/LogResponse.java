package org.zmy.observabilityplatform.logging.interfaces.rest.response;

import org.zmy.observabilityplatform.logging.application.dto.LogView;

import java.time.Instant;
import java.util.Map;

public record LogResponse(
        String id,
        String batchId,
        Instant timestamp,
        Instant receivedAt,
        String service,
        String environment,
        String level,
        String traceId,
        String rawMessage,
        String message,
        String fingerprint,
        Map<String, Object> attributes) {

    public static LogResponse from(LogView view) {
        return new LogResponse(view.id(), view.batchId(), view.timestamp(), view.receivedAt(), view.service(),
                view.environment(), view.level(), view.traceId(), view.rawMessage(), view.message(),
                view.fingerprint(), view.attributes());
    }
}
