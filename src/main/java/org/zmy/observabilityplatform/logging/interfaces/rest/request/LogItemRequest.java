package org.zmy.observabilityplatform.logging.interfaces.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogItemRequest {
    /** 日志在业务服务中产生的时间。 */
    private Instant timestamp;

    /** 未解析的日志正文。 */
    @NotBlank
    private String content;

    /** 日志正文格式，例如 JSON 或 TEXT。 */
    @NotBlank
    private String format;

    /** 分布式调用链标识。 */
    @Size(max = 64)
    private String traceId;

    /** 当前调用跨度标识。 */
    @Size(max = 32)
    private String spanId;

    /** 上游调用跨度标识。 */
    @Size(max = 32)
    private String parentSpanId;

    /** 业务请求标识。 */
    @Size(max = 255)
    private String requestId;

    /** 产生该日志的操作或接口名称。 */
    @Size(max = 255)
    private String operation;

    /** 调用跨度类型，例如 SERVER 或 CLIENT。 */
    @Size(max = 20)
    private String spanKind;

    /** 对应请求的 HTTP 或 RPC 状态码。 */
    private Integer statusCode;

    /** 对应操作是否执行成功。 */
    private Boolean success;

    /** 业务或系统错误码。 */
    @Size(max = 255)
    private String errorCode;

    /** 对应操作的耗时，单位为毫秒。 */
    private Long durationMs;

    /** 业务服务补充的结构化属性。 */
    private Map<String, Object> attributes;
}
