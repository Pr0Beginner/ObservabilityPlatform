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
    /** 分布式调用链标识。 */
    private String traceId;

    /** 调用链中最早的开始时间。 */
    private Instant startedAt;

    /** 调用链中最晚的结束时间。 */
    private Instant endedAt;

    /** 整条调用链的持续时间，单位为毫秒。 */
    private long durationMs;

    /** 整条调用链是否执行成功。 */
    private boolean success;

    /** 参与本次调用的服务名称集合。 */
    private List<String> services;

    /** 调用链的根跨度节点。 */
    private List<TraceSpanResponse> roots;

    /** 因缺少跨度信息而无法挂载到调用树的日志。 */
    private List<LogResponse> unassignedLogs;

    /** 查询结果是否因数量上限被截断。 */
    private boolean truncated;

    public static TraceResponse from(TraceCallTree tree) {
        return new TraceResponse(tree.getTraceId(), tree.startedAt(), tree.endedAt(), tree.durationMs(),
                tree.isSuccessful(), tree.services(),
                tree.roots().stream().map(span -> TraceSpanResponse.from(span, tree)).toList(),
                tree.getUnassignedLogs().stream().map(LogView::from).map(LogResponse::from).toList(),
                tree.isTruncated());
    }
}
