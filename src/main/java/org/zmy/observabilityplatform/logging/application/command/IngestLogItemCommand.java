package org.zmy.observabilityplatform.logging.application.command;

import lombok.Value;

import java.time.Instant;
import java.util.Map;

@Value
public class IngestLogItemCommand {
    /** 日志在业务服务中产生的时间。 */
    Instant timestamp;

    /** 未解析的日志正文。 */
    String content;

    /** 日志正文格式，例如 JSON 或 TEXT。 */
    String format;

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

    /** 业务服务补充的结构化属性。 */
    Map<String, Object> attributes;
}
