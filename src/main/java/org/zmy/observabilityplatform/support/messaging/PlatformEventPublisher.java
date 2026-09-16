package org.zmy.observabilityplatform.support.messaging;

import org.zmy.observabilityplatform.diagnosis.domain.DiagnosisRequestedEvent;
import org.zmy.observabilityplatform.logging.domain.RawLogBatch;
import reactor.core.publisher.Mono;

public interface PlatformEventPublisher {
    Mono<Void> publish(RawLogBatch batch);

    Mono<Void> publish(DiagnosisRequestedEvent event);
}
