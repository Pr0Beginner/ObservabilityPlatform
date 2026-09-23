package org.zmy.observabilityplatform.logging.interfaces.rest.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

/**
 * 日志查询接口的 HTTP 参数对象。
 */
@Data
@NoArgsConstructor
public class LogSearchRequest {
    /** 查询时间范围的起点，包含该时刻。 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant from;

    /** 查询时间范围的终点，包含该时刻。 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant to;

    /** 产生日志的服务名称。 */
    @Size(max = 120)
    private String service;

    /** 日志所属环境，例如 local、test 或 production。 */
    @Size(max = 80)
    private String environment;

    /** 日志级别，例如 INFO、WARN 或 ERROR。 */
    @Size(max = 20)
    private String level;

    /** 分布式调用链标识。 */
    @Size(max = 64)
    private String traceId;

    /** 调用链中的单次调用标识。 */
    @Size(max = 32)
    private String spanId;

    /** 业务请求标识。 */
    @Size(max = 255)
    private String requestId;

    /** 在日志正文中检索的关键字。 */
    @Size(max = 500)
    private String keyword;

    /** 归并同类错误使用的日志指纹。 */
    @Size(max = 255)
    private String fingerprint;

    /** 上一页返回的不透明游标；首次查询不传。 */
    @Size(max = 1024)
    private String cursor;

    /** 本页最多返回的日志数量。 */
    @Min(1)
    @Max(200)
    private int size = 100;
}
