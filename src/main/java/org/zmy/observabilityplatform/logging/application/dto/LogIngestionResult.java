package org.zmy.observabilityplatform.logging.application.dto;

import lombok.Value;

@Value
public class LogIngestionResult {
    /** 客户端提交的日志批次标识。 */
    String batchId;

    /** 平台已接受的日志条数。 */
    int accepted;

    /** 当前批次的接收状态。 */
    String status;
}
