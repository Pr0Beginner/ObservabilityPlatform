package org.zmy.observabilityplatform.shared.messaging.infrastructure.publisher;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.zmy.observabilityplatform.shared.messaging.application.publisher.DeadLetterReplayPublisher;
import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterMessage;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class KafkaDeadLetterReplayPublisher implements DeadLetterReplayPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaDeadLetterReplayPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public Mono<Void> publish(DeadLetterMessage message) {
        return Mono.fromFuture(kafkaTemplate.send(
                message.getOriginalTopic(), message.getMessageKey(), message.getPayload())).then();
    }
}
