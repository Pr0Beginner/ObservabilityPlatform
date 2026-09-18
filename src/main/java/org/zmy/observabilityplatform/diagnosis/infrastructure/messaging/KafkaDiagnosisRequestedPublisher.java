package org.zmy.observabilityplatform.diagnosis.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.zmy.observabilityplatform.diagnosis.application.publisher.DiagnosisRequestedPublisher;
import org.zmy.observabilityplatform.diagnosis.domain.event.DiagnosisRequestedEvent;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class KafkaDiagnosisRequestedPublisher implements DiagnosisRequestedPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public KafkaDiagnosisRequestedPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                            ObjectMapper objectMapper,
                                            @Value("${app.kafka.topics.diagnosis-requested}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Override
    public Mono<Void> publish(DiagnosisRequestedEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            return Mono.fromFuture(kafkaTemplate.send(topic, event.getTaskId(), json)).then();
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }
    }
}
