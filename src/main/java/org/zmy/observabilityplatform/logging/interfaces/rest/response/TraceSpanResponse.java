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
    /** 当前调用跨度标识。 */
    private String spanId;

    /** 上游调用跨度标识。 */
    private String parentSpanId;

    /** 执行当前调用的服务名称。 */
    private String service;

    /** 当前调用的操作或接口名称。 */
    private String operation;

    /** 调用跨度类型，例如 SERVER 或 CLIENT。 */
    private String spanKind;

    /** 当前调用的开始时间。 */
    private Instant startedAt;

    /** 当前调用的结束时间。 */
    private Instant endedAt;

    /** 当前调用的持续时间，单位为毫秒。 */
    private long durationMs;

    /** 当前调用是否执行成功。 */
    private boolean success;

    /** 当前调用的 HTTP 或 RPC 状态码。 */
    private Integer statusCode;

    /** 当前调用的业务或系统错误码。 */
    private String errorCode;

    /** 归属于当前跨度的日志。 */
    private List<LogResponse> logs;

    /** 当前跨度直接调用的下游跨度。 */
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
