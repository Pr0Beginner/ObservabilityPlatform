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
    /** 日志唯一标识。 */
    private String id;

    /** 日志所属的接收批次标识。 */
    private String batchId;

    /** 日志在业务服务中产生的时间。 */
    private Instant timestamp;

    /** 日志被平台接收的时间。 */
    private Instant receivedAt;

    /** 产生日志的服务名称。 */
    private String service;

    /** 日志所属环境。 */
    private String environment;

    /** 日志级别。 */
    private String level;

    /** 分布式调用链标识。 */
    private String traceId;

    /** 当前调用跨度标识。 */
    private String spanId;

    /** 上游调用跨度标识。 */
    private String parentSpanId;

    /** 业务请求标识。 */
    private String requestId;

    /** 产生该日志的操作或接口名称。 */
    private String operation;

    /** 调用跨度类型，例如 SERVER 或 CLIENT。 */
    private String spanKind;

    /** 对应请求的 HTTP 或 RPC 状态码。 */
    private Integer statusCode;

    /** 对应操作是否执行成功。 */
    private Boolean success;

    /** 业务或系统错误码。 */
    private String errorCode;

    /** 对应操作的耗时，单位为毫秒。 */
    private Long durationMs;

    /** 脱敏后的原始日志正文。 */
    private String rawMessage;

    /** 解析后的可读日志消息。 */
    private String message;

    /** 用于聚合同类错误的日志指纹。 */
    private String fingerprint;

    /** 日志携带的其他结构化属性。 */
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
