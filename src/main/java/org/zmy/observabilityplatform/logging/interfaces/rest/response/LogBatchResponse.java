package org.zmy.observabilityplatform.logging.interfaces.rest.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.zmy.observabilityplatform.logging.application.dto.LogIngestionResult;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogBatchResponse {
    /** 客户端提交的日志批次标识。 */
    private String batchId;

    /** 平台已接受的日志条数。 */
    private int accepted;

    /** 当前批次的接收状态。 */
    private String status;

    public static LogBatchResponse from(LogIngestionResult result) {
        return new LogBatchResponse(result.getBatchId(), result.getAccepted(), result.getStatus());
    }
}
