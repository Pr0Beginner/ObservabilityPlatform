package org.zmy.observabilityplatform.logging.application.dto;

public record LogIngestionResult(String batchId, int accepted, String status) {
}
