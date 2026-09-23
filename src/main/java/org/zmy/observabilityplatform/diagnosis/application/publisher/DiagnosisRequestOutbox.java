package org.zmy.observabilityplatform.diagnosis.application.publisher;

import org.zmy.observabilityplatform.diagnosis.domain.event.DiagnosisRequestedEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Delivery port for requested events durably saved with their tasks. */
public interface DiagnosisRequestOutbox {
    Flux<DiagnosisRequestedEvent> pending(int limit);

    Mono<Void> acknowledge(String eventId);
}
