package org.zmy.observabilityplatform.shared.messaging.interfaces.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;
import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterMessage;
import org.zmy.observabilityplatform.shared.messaging.domain.repository.DeadLetterRepository;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

@Component
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class KafkaDeadLetterConsumer {
    private final DeadLetterRepository repository;
    private final Clock clock;

    public KafkaDeadLetterConsumer(DeadLetterRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @KafkaListener(topics = {"${app.kafka.topics.logs-raw}.DLT", "${app.kafka.topics.diagnosis-completed}.DLT"},
            groupId = "observability-dead-letter-recorder")
    public void consume(ConsumerRecord<String, String> record) {
        String dltSuffix = ".DLT";
        String originalTopic = record.topic().endsWith(dltSuffix)
                ? record.topic().substring(0, record.topic().length() - dltSuffix.length()) : record.topic();
        Header reasonHeader = record.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE);
        String reason = reasonHeader == null ? "Kafka listener failed"
                : new String(reasonHeader.value(), StandardCharsets.UTF_8);
        repository.saveIfAbsent(DeadLetterMessage.captured(originalTopic, record.key(), record.value(), reason,
                record.partition(), record.offset(), clock.instant())).block();
    }
}
