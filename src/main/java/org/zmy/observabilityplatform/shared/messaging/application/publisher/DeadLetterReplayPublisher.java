package org.zmy.observabilityplatform.shared.messaging.application.publisher;

import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterMessage;
import reactor.core.publisher.Mono;

public interface DeadLetterReplayPublisher {
    Mono<Void> publish(DeadLetterMessage message);
}
