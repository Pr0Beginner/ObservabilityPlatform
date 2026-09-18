package org.zmy.observabilityplatform.bootstrap.configuration;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class KafkaTopicConfiguration {
    @Bean
    NewTopic logsRawTopic(@Value("${app.kafka.topics.logs-raw}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic diagnosisRequestedTopic(@Value("${app.kafka.topics.diagnosis-requested}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic diagnosisCompletedTopic(@Value("${app.kafka.topics.diagnosis-completed}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
