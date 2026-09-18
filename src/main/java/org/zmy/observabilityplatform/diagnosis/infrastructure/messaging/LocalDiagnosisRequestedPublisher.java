package org.zmy.observabilityplatform.diagnosis.infrastructure.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.zmy.observabilityplatform.diagnosis.application.publisher.DiagnosisRequestedPublisher;
import org.zmy.observabilityplatform.diagnosis.domain.event.DiagnosisRequestedEvent;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "local", matchIfMissing = true)
public class LocalDiagnosisRequestedPublisher implements DiagnosisRequestedPublisher {
    @Override
    public Mono<Void> publish(DiagnosisRequestedEvent event) {
        return Mono.empty();
    }
}
