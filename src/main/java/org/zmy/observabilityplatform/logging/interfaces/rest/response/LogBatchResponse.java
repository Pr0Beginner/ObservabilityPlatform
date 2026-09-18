package org.zmy.observabilityplatform.logging.interfaces.rest.response;

import org.zmy.observabilityplatform.logging.application.dto.LogIngestionResult;

public record LogBatchResponse(String batchId, int accepted, String status) {
    public static LogBatchResponse from(LogIngestionResult result) {
        return new LogBatchResponse(result.batchId(), result.accepted(), result.status());
    }
}
