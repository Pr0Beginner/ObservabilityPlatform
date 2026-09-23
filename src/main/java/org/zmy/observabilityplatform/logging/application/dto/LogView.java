package org.zmy.observabilityplatform.logging.application.dto;

import lombok.Value;
import org.zmy.observabilityplatform.logging.domain.model.LogEntry;

import java.time.Instant;
import java.util.Map;

@Value
public class LogView {
    /** 日志唯一标识。 */
    String id;

    /** 日志所属的接收批次标识。 */
    String batchId;

    /** 日志在业务服务中产生的时间。 */
    Instant timestamp;

    /** 日志被平台接收的时间。 */
    Instant receivedAt;

    /** 产生日志的服务名称。 */
    String service;

    /** 日志所属环境。 */
    String environment;

    /** 日志级别。 */
    String level;

    /** 分布式调用链标识。 */
    String traceId;

    /** 当前调用跨度标识。 */
    String spanId;

    /** 上游调用跨度标识。 */
    String parentSpanId;

    /** 业务请求标识。 */
    String requestId;

    /** 产生该日志的操作或接口名称。 */
    String operation;

    /** 调用跨度类型，例如 SERVER 或 CLIENT。 */
    String spanKind;

    /** 对应请求的 HTTP 或 RPC 状态码。 */
    Integer statusCode;

    /** 对应操作是否执行成功。 */
    Boolean success;

    /** 业务或系统错误码。 */
    String errorCode;

    /** 对应操作的耗时，单位为毫秒。 */
    Long durationMs;

    /** 脱敏后的原始日志正文。 */
    String rawMessage;

    /** 解析后的可读日志消息。 */
    String message;

    /** 用于聚合同类错误的日志指纹。 */
    String fingerprint;

    /** 日志携带的其他结构化属性。 */
    Map<String, Object> attributes;

    public static LogView from(LogEntry entry) {
        return new LogView(entry.getId(), entry.getBatchId(), entry.getTimestamp(), entry.getReceivedAt(),
                entry.getService(), entry.getEnvironment(), entry.getLevel(), entry.getTraceId(),
                entry.getSpanId(), entry.getParentSpanId(), entry.getRequestId(), entry.getOperation(),
                entry.getSpanKind(), entry.getStatusCode(), entry.getSuccess(), entry.getErrorCode(),
                entry.getDurationMs(), entry.getRawMessage(), entry.getMessage(), entry.getFingerprint(),
                entry.getAttributes());
    }
}
