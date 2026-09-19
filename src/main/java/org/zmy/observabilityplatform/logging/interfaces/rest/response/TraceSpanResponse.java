package org.zmy.observabilityplatform.logging.interfaces.rest.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.zmy.observabilityplatform.logging.application.dto.LogView;
import org.zmy.observabilityplatform.logging.domain.model.TraceCallTree;
import org.zmy.observabilityplatform.logging.domain.model.TraceSpan;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TraceSpanResponse {
    private String spanId;
    private String parentSpanId;
    private String service;
    private String operation;
    private String spanKind;
    private Instant startedAt;
    private Instant endedAt;
    private long durationMs;
    private boolean success;
    private Integer statusCode;
    private String errorCode;
    private List<LogResponse> logs;
    private List<TraceSpanResponse> children;

    public static TraceSpanResponse from(TraceSpan span, TraceCallTree tree) {
        List<LogResponse> logs = span.getLogs().stream()
                .map(LogView::from)
                .map(LogResponse::from)
                .toList();
        List<TraceSpanResponse> children = tree.childrenOf(span.getSpanId()).stream()
                .map(child -> from(child, tree))
                .toList();
        return new TraceSpanResponse(span.getSpanId(), span.getParentSpanId(), span.getService(),
                span.getOperation(), span.getSpanKind(), span.getStartedAt(), span.getEndedAt(),
                span.getDurationMs(), span.isSuccess(), span.getStatusCode(), span.getErrorCode(), logs, children);
    }
}
