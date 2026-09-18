package org.zmy.observabilityplatform.logging.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.zmy.observabilityplatform.logging.application.publisher.RawLogBatchPublisher;
import org.zmy.observabilityplatform.logging.domain.model.RawLogBatch;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class KafkaRawLogBatchPublisher implements RawLogBatchPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public KafkaRawLogBatchPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                     ObjectMapper objectMapper,
                                     @Value("${app.kafka.topics.logs-raw}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Override
    public Mono<Void> publish(RawLogBatch batch) {
        try {
            String json = objectMapper.writeValueAsString(batch);
            return Mono.fromFuture(kafkaTemplate.send(topic, batch.batchId(), json)).then();
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }
    }
}
