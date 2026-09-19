package org.zmy.observabilityplatform.shared.messaging.infrastructure.publisher;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.zmy.observabilityplatform.shared.exception.BusinessConflictException;
import org.zmy.observabilityplatform.shared.messaging.application.publisher.DeadLetterReplayPublisher;
import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterMessage;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "local", matchIfMissing = true)
public class LocalDeadLetterReplayPublisher implements DeadLetterReplayPublisher {
    @Override
    public Mono<Void> publish(DeadLetterMessage message) {
        return Mono.error(new BusinessConflictException("Kafka replay is only available in external adapter mode"));
    }
}
