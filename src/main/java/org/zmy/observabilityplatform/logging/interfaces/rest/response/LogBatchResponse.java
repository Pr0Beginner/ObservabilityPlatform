package org.zmy.observabilityplatform.logging.interfaces.rest.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.zmy.observabilityplatform.logging.application.dto.LogIngestionResult;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogBatchResponse {
    private String batchId;
    private int accepted;
    private String status;

    public static LogBatchResponse from(LogIngestionResult result) {
        return new LogBatchResponse(result.getBatchId(), result.getAccepted(), result.getStatus());
    }
}
