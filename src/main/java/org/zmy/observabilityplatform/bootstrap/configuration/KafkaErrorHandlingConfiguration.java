package org.zmy.observabilityplatform.bootstrap.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import org.zmy.observabilityplatform.shared.exception.BusinessConflictException;
import org.zmy.observabilityplatform.shared.exception.NotFoundException;

@Configuration
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class KafkaErrorHandlingConfiguration {
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate,
                                         @Value("${app.kafka.retry.interval-ms:1000}") long intervalMs,
                                         @Value("${app.kafka.retry.max-attempts:3}") long maxAttempts) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, error) -> new TopicPartition(record.topic()
                        + (record.topic().endsWith(".DLT") ? ".PARKED" : ".DLT"), record.partition()));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(intervalMs, Math.max(0, maxAttempts - 1)));
        handler.addNotRetryableExceptions(JsonProcessingException.class, IllegalArgumentException.class,
                BusinessConflictException.class, NotFoundException.class);
        return handler;
    }
}
