package org.zmy.observabilityplatform.logging.infrastructure.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.zmy.observabilityplatform.logging.application.publisher.RawLogBatchPublisher;
import org.zmy.observabilityplatform.logging.application.service.LogProcessingService;
import org.zmy.observabilityplatform.logging.domain.model.RawLogBatch;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "local", matchIfMissing = true)
public class LocalRawLogBatchPublisher implements RawLogBatchPublisher {
    private final LogProcessingService logProcessingService;

    public LocalRawLogBatchPublisher(LogProcessingService logProcessingService) {
        this.logProcessingService = logProcessingService;
    }

    @Override
    public Mono<Void> publish(RawLogBatch batch) {
        return logProcessingService.process(batch);
    }
}
