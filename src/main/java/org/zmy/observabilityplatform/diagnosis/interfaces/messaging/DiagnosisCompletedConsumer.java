package org.zmy.observabilityplatform.diagnosis.interfaces.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.zmy.observabilityplatform.diagnosis.application.service.DiagnosisService;
import org.zmy.observabilityplatform.diagnosis.domain.event.DiagnosisCompletedEvent;

@Component
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class DiagnosisCompletedConsumer {
    private final ObjectMapper objectMapper;
    private final DiagnosisService diagnosisService;

    public DiagnosisCompletedConsumer(ObjectMapper objectMapper, DiagnosisService diagnosisService) {
        this.objectMapper = objectMapper;
        this.diagnosisService = diagnosisService;
    }

    @KafkaListener(topics = "${app.kafka.topics.diagnosis-completed}", groupId = "observability-diagnosis-result")
    public void consume(String payload) throws JsonProcessingException {
        DiagnosisCompletedEvent event = objectMapper.readValue(payload, DiagnosisCompletedEvent.class);
        diagnosisService.complete(event).block();
    }
}
