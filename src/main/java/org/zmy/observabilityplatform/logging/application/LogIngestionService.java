package org.zmy.observabilityplatform.logging.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.logging.domain.RawLogBatch;
import org.zmy.observabilityplatform.support.messaging.PlatformEventPublisher;
import reactor.core.publisher.Mono;

@Service
public class LogIngestionService {
    private final PlatformEventPublisher eventPublisher;
    private final int maxBatchSize;

    public LogIngestionService(PlatformEventPublisher eventPublisher,
                               @Value("${app.ingestion.max-batch-size:1000}") int maxBatchSize) {
        this.eventPublisher = eventPublisher;
        this.maxBatchSize = maxBatchSize;
    }

    public Mono<IngestionResult> ingest(RawLogBatch batch) {
        if (batch.logs().isEmpty()) {
            return Mono.error(new IllegalArgumentException("The log batch must not be empty"));
        }
        if (batch.logs().size() > maxBatchSize) {
            return Mono.error(new IllegalArgumentException("Batch exceeds maximum size of " + maxBatchSize));
        }
        return eventPublisher.publish(batch)
                .thenReturn(new IngestionResult(batch.batchId(), batch.logs().size(), "ACCEPTED"));
    }

    public record IngestionResult(String batchId, int accepted, String status) { }
}
