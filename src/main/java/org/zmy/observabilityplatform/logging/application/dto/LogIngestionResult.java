package org.zmy.observabilityplatform.logging.application.dto;

import lombok.Value;

@Value
public class LogIngestionResult {
    String batchId;
    int accepted;
    String status;
}
