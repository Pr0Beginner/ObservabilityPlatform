package org.zmy.observabilityplatform.support.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.zmy.observabilityplatform.diagnosis.application.DiagnosisService;
import org.zmy.observabilityplatform.diagnosis.domain.DiagnosisCompletedEvent;
import org.zmy.observabilityplatform.logging.application.LogProcessingService;
import org.zmy.observabilityplatform.logging.domain.RawLogBatch;

@Component
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class KafkaEventConsumers {
    private final ObjectMapper objectMapper;
    private final LogProcessingService logProcessingService;
    private final DiagnosisService diagnosisService;

    public KafkaEventConsumers(ObjectMapper objectMapper,
                               LogProcessingService logProcessingService,
                               DiagnosisService diagnosisService) {
        this.objectMapper = objectMapper;
        this.logProcessingService = logProcessingService;
        this.diagnosisService = diagnosisService;
    }

    @KafkaListener(topics = "${app.kafka.topics.logs-raw}", groupId = "observability-log-processor")
    public void consumeRawLogs(String payload) throws JsonProcessingException {
        RawLogBatch batch = objectMapper.readValue(payload, RawLogBatch.class);
        logProcessingService.process(batch).block();
    }

    @KafkaListener(topics = "${app.kafka.topics.diagnosis-completed}", groupId = "observability-diagnosis-result")
    public void consumeDiagnosisCompleted(String payload) throws JsonProcessingException {
        DiagnosisCompletedEvent event = objectMapper.readValue(payload, DiagnosisCompletedEvent.class);
        diagnosisService.complete(event).block();
    }
}
