package org.zmy.observabilityplatform.logging.interfaces.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.zmy.observabilityplatform.logging.application.service.LogProcessingService;
import org.zmy.observabilityplatform.logging.domain.model.RawLogBatch;

@Component
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class RawLogBatchConsumer {
    private final ObjectMapper objectMapper;
    private final LogProcessingService logProcessingService;

    public RawLogBatchConsumer(ObjectMapper objectMapper, LogProcessingService logProcessingService) {
        this.objectMapper = objectMapper;
        this.logProcessingService = logProcessingService;
    }

    @KafkaListener(topics = "${app.kafka.topics.logs-raw}", groupId = "observability-log-processor")
    public void consume(String payload) throws JsonProcessingException {
        RawLogBatch batch = objectMapper.readValue(payload, RawLogBatch.class);
        // 等待整批处理完成后再返回，确保消费位点不会先于持久化推进。
        logProcessingService.process(batch).block();
    }
}
