package org.zmy.observabilityplatform.logging.application.query;

import lombok.Value;

import java.time.Instant;

@Value
public class LogSearchQuery {
    /** 查询时间范围的起点，包含该时刻。 */
    Instant from;

    /** 查询时间范围的终点，包含该时刻。 */
    Instant to;

    /** 产生日志的服务名称。 */
    String service;

    /** 日志所属环境。 */
    String environment;

    /** 日志级别。 */
    String level;

    /** 分布式调用链标识。 */
    String traceId;

    /** 调用链中的单次调用标识。 */
    String spanId;

    /** 业务请求标识。 */
    String requestId;

    /** 日志全文检索关键字。 */
    String keyword;

    /** 同类错误的聚合指纹。 */
    String fingerprint;

    /** 当前页的查询起点；为空表示从第一页开始。 */
    LogCursor cursor;

    /** 本页期望返回的日志数量。 */
    int size;
}
