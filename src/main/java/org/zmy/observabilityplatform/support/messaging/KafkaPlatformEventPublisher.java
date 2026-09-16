package org.zmy.observabilityplatform.support.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.zmy.observabilityplatform.diagnosis.domain.DiagnosisRequestedEvent;
import org.zmy.observabilityplatform.logging.domain.RawLogBatch;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class KafkaPlatformEventPublisher implements PlatformEventPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String logsRawTopic;
    private final String diagnosisRequestedTopic;

    public KafkaPlatformEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.kafka.topics.logs-raw}") String logsRawTopic,
            @Value("${app.kafka.topics.diagnosis-requested}") String diagnosisRequestedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.logsRawTopic = logsRawTopic;
        this.diagnosisRequestedTopic = diagnosisRequestedTopic;
    }

    @Override
    public Mono<Void> publish(RawLogBatch batch) {
        return send(logsRawTopic, batch.batchId(), batch);
    }

    @Override
    public Mono<Void> publish(DiagnosisRequestedEvent event) {
        return send(diagnosisRequestedTopic, event.taskId(), event);
    }

    private Mono<Void> send(String topic, String key, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            return Mono.fromFuture(kafkaTemplate.send(topic, key, json)).then();
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }
    }
}
