package org.zmy.observabilityplatform.diagnosis.application.publisher;

import org.zmy.observabilityplatform.diagnosis.domain.event.DiagnosisRequestedEvent;
import reactor.core.publisher.Mono;

public interface DiagnosisRequestedPublisher {
    Mono<Void> publish(DiagnosisRequestedEvent event);
}
