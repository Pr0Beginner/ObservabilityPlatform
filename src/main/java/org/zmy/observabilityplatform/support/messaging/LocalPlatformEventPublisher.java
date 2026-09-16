package org.zmy.observabilityplatform.support.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.zmy.observabilityplatform.diagnosis.domain.DiagnosisRequestedEvent;
import org.zmy.observabilityplatform.logging.application.LogProcessingService;
import org.zmy.observabilityplatform.logging.domain.RawLogBatch;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "local", matchIfMissing = true)
public class LocalPlatformEventPublisher implements PlatformEventPublisher {
    private final LogProcessingService logProcessingService;

    public LocalPlatformEventPublisher(LogProcessingService logProcessingService) {
        this.logProcessingService = logProcessingService;
    }

    @Override
    public Mono<Void> publish(RawLogBatch batch) {
        return logProcessingService.process(batch);
    }

    @Override
    public Mono<Void> publish(DiagnosisRequestedEvent event) {
        return Mono.empty();
    }
}
