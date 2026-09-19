package org.zmy.observabilityplatform.logging.interfaces.rest.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.zmy.observabilityplatform.logging.application.dto.LogView;
import org.zmy.observabilityplatform.logging.domain.model.TraceCallTree;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TraceResponse {
    private String traceId;
    private Instant startedAt;
    private Instant endedAt;
    private long durationMs;
    private boolean success;
    private List<String> services;
    private List<TraceSpanResponse> roots;
    private List<LogResponse> unassignedLogs;
    private boolean truncated;

    public static TraceResponse from(TraceCallTree tree) {
        return new TraceResponse(tree.getTraceId(), tree.startedAt(), tree.endedAt(), tree.durationMs(),
                tree.isSuccessful(), tree.services(),
                tree.roots().stream().map(span -> TraceSpanResponse.from(span, tree)).toList(),
                tree.getUnassignedLogs().stream().map(LogView::from).map(LogResponse::from).toList(),
                tree.isTruncated());
    }
}
