package org.zmy.observabilityplatform.logging.interfaces.rest.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.zmy.observabilityplatform.logging.application.dto.LogView;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogResponse {
    private String id;
    private String batchId;
    private Instant timestamp;
    private Instant receivedAt;
    private String service;
    private String environment;
    private String level;
    private String traceId;
    private String spanId;
    private String parentSpanId;
    private String requestId;
    private String operation;
    private String spanKind;
    private Integer statusCode;
    private Boolean success;
    private String errorCode;
    private Long durationMs;
    private String rawMessage;
    private String message;
    private String fingerprint;
    private Map<String, Object> attributes;

    public static LogResponse from(LogView view) {
        return new LogResponse(view.getId(), view.getBatchId(), view.getTimestamp(), view.getReceivedAt(),
                view.getService(), view.getEnvironment(), view.getLevel(), view.getTraceId(),
                view.getSpanId(), view.getParentSpanId(), view.getRequestId(), view.getOperation(),
                view.getSpanKind(), view.getStatusCode(), view.getSuccess(), view.getErrorCode(),
                view.getDurationMs(), view.getRawMessage(), view.getMessage(), view.getFingerprint(),
                view.getAttributes());
    }
}
