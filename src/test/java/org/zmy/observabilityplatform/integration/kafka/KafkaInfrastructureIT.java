package org.zmy.observabilityplatform.integration.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.zmy.observabilityplatform.bootstrap.configuration.KafkaErrorHandlingConfiguration;
import org.zmy.observabilityplatform.bootstrap.configuration.KafkaTopicConfiguration;
import org.zmy.observabilityplatform.logging.application.service.LogProcessingService;
import org.zmy.observabilityplatform.logging.domain.model.RawLogBatch;
import org.zmy.observabilityplatform.logging.domain.model.RawLogRecord;
import org.zmy.observabilityplatform.logging.infrastructure.messaging.KafkaRawLogBatchPublisher;
import org.zmy.observabilityplatform.logging.interfaces.messaging.RawLogBatchConsumer;
import org.zmy.observabilityplatform.shared.exception.RetryableDependencyException;
import org.zmy.observabilityplatform.shared.messaging.application.service.DeadLetterService;
import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterMessage;
import org.zmy.observabilityplatform.shared.messaging.domain.repository.DeadLetterRepository;
import org.zmy.observabilityplatform.shared.messaging.infrastructure.publisher.KafkaDeadLetterReplayPublisher;
import org.zmy.observabilityplatform.shared.messaging.interfaces.kafka.KafkaDeadLetterConsumer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Tag("integration")
@Testcontainers
@SpringBootTest(classes = KafkaInfrastructureIT.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "app.adapters.mode=external",
                "app.kafka.retry.interval-ms=100",
                "app.kafka.retry.max-attempts=3",
                "spring.kafka.consumer.auto-offset-reset=earliest"
        })
class KafkaInfrastructureIT {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String TOPIC_SUFFIX = UUID.randomUUID().toString().replace("-", "");
    private static final String LOGS_TOPIC = "it.logs.raw." + TOPIC_SUFFIX;
    private static final String DIAGNOSIS_REQUESTED_TOPIC = "it.diagnosis.requested." + TOPIC_SUFFIX;
    private static final String DIAGNOSIS_COMPLETED_TOPIC = "it.diagnosis.completed." + TOPIC_SUFFIX;

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.9.1"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.kafka.topics.logs-raw", () -> LOGS_TOPIC);
        registry.add("app.kafka.topics.diagnosis-requested", () -> DIAGNOSIS_REQUESTED_TOPIC);
        registry.add("app.kafka.topics.diagnosis-completed", () -> DIAGNOSIS_COMPLETED_TOPIC);
    }

    @Autowired
    private KafkaRawLogBatchPublisher publisher;
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private LogProcessingService processingService;
    @Autowired
    private ToggleDeadLetterRepository deadLetterRepository;
    @Autowired
    private DeadLetterService deadLetterService;
    @Autowired
    private ParkedMessageCollector parkedCollector;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetState() {
        Mockito.reset(processingService);
        when(processingService.process(any())).thenReturn(Mono.empty());
        deadLetterRepository.clear();
        parkedCollector.clear();
    }

    @Test
    void retriesTransientFailureAndCommitsAfterSuccess() {
        AtomicInteger attempts = new AtomicInteger();
        when(processingService.process(any())).thenAnswer(invocation -> attempts.incrementAndGet() < 3
                ? Mono.error(RetryableDependencyException.unavailable("Elasticsearch",
                        new IllegalStateException("temporary outage")))
                : Mono.empty());

        publisher.publish(batch("batch-retry")).block(TIMEOUT);

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(attempts.get()).isEqualTo(3));
        assertThat(deadLetterRepository.all()).isEmpty();
    }

    @Test
    void sendsNonRetryablePayloadToDeadLetterAndPersistsIt() throws Exception {
        String key = "invalid-" + UUID.randomUUID();

        kafkaTemplate.send(LOGS_TOPIC, key, "{invalid-json").get(10, TimeUnit.SECONDS);

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(deadLetterRepository.findByKey(key)).isPresent());
        DeadLetterMessage message = deadLetterRepository.findByKey(key).orElseThrow();
        assertThat(message.getOriginalTopic()).isEqualTo(LOGS_TOPIC);
        assertThat(message.getPayload()).isEqualTo("{invalid-json");
        assertThat(message.getFailureReason()).isNotBlank();
        Mockito.verifyNoInteractions(processingService);
    }

    @Test
    void replaysRecordedMessageToItsOriginalTopicAndMarksItReplayed() throws Exception {
        RawLogBatch batch = batch("batch-replay");
        DeadLetterMessage recorded = DeadLetterMessage.captured(LOGS_TOPIC, batch.getBatchId(),
                objectMapper.writeValueAsString(batch), "temporary failure", 0, 100, Instant.now());
        deadLetterRepository.saveIfAbsent(recorded).block(TIMEOUT);

        DeadLetterMessage replayed = deadLetterService.replay(recorded.getId()).block(TIMEOUT);

        assertThat(replayed.getReplayedAt()).isNotNull();
        await().atMost(TIMEOUT).untilAsserted(() ->
                Mockito.verify(processingService).process(batch));
    }

    @Test
    void parksDeadLetterWhenItsRecorderAlsoFails() throws Exception {
        String key = "parked-" + UUID.randomUUID();
        deadLetterRepository.failCaptures();

        kafkaTemplate.send(LOGS_TOPIC, key, "{still-invalid").get(10, TimeUnit.SECONDS);

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(parkedCollector.findByKey(key)).isPresent());
        ConsumerRecord<String, String> parked = parkedCollector.findByKey(key).orElseThrow();
        assertThat(parked.topic()).isEqualTo(LOGS_TOPIC + ".DLT.PARKED");
        assertThat(parked.value()).isEqualTo("{still-invalid");
    }

    private RawLogBatch batch(String batchId) {
        Instant timestamp = Instant.parse("2026-09-20T10:00:00Z");
        RawLogRecord record = RawLogRecord.capture(batchId, 0, timestamp,
                "{\"level\":\"INFO\",\"message\":\"order accepted\"}", "JSON",
                "0af7651916cd43dd8448eb211c80319c", "b7ad6b7169203331", null,
                "request-it", "POST /orders", "SERVER", 202, true,
                null, 12L, Map.of());
        return RawLogBatch.receive(batchId, "orders", "integration", timestamp, List.of(record));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableKafka
    @Import({KafkaErrorHandlingConfiguration.class, KafkaTopicConfiguration.class,
            KafkaRawLogBatchPublisher.class, RawLogBatchConsumer.class,
            KafkaDeadLetterConsumer.class, KafkaDeadLetterReplayPublisher.class,
            DeadLetterService.class})
    static class TestApplication {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        LogProcessingService processingService() {
            return Mockito.mock(LogProcessingService.class);
        }

        @Bean
        ToggleDeadLetterRepository deadLetterRepository() {
            return new ToggleDeadLetterRepository();
        }

        @Bean
        ParkedMessageCollector parkedMessageCollector() {
            return new ParkedMessageCollector();
        }
    }

    static final class ToggleDeadLetterRepository implements DeadLetterRepository {
        private final Map<String, DeadLetterMessage> messages = new ConcurrentHashMap<>();
        private final AtomicBoolean failCaptures = new AtomicBoolean();

        @Override
        public Mono<DeadLetterMessage> saveIfAbsent(DeadLetterMessage message) {
            if (failCaptures.get()) {
                return Mono.error(RetryableDependencyException.unavailable(
                        "dead-letter repository", new IllegalStateException("forced failure")));
            }
            messages.putIfAbsent(message.getId(), message);
            return Mono.just(messages.get(message.getId()));
        }

        @Override
        public Mono<DeadLetterMessage> save(DeadLetterMessage message) {
            messages.put(message.getId(), message);
            return Mono.just(message);
        }

        @Override
        public Mono<DeadLetterMessage> findById(String id) {
            return Mono.justOrEmpty(messages.get(id));
        }

        @Override
        public Flux<DeadLetterMessage> findAll(int limit) {
            return Flux.fromStream(messages.values().stream()
                    .sorted(Comparator.comparing(DeadLetterMessage::getFailedAt).reversed())
                    .limit(limit));
        }

        List<DeadLetterMessage> all() {
            return List.copyOf(messages.values());
        }

        java.util.Optional<DeadLetterMessage> findByKey(String key) {
            return messages.values().stream()
                    .filter(message -> key.equals(message.getMessageKey()))
                    .findFirst();
        }

        void failCaptures() {
            failCaptures.set(true);
        }

        void clear() {
            failCaptures.set(false);
            messages.clear();
        }
    }

    static final class ParkedMessageCollector {
        private final BlockingQueue<ConsumerRecord<String, String>> records = new LinkedBlockingQueue<>();

        @KafkaListener(topics = "${app.kafka.topics.logs-raw}.DLT.PARKED",
                groupId = "observability-integration-parked")
        void consume(ConsumerRecord<String, String> record) {
            records.add(record);
        }

        java.util.Optional<ConsumerRecord<String, String>> findByKey(String key) {
            return records.stream().filter(record -> key.equals(record.key())).findFirst();
        }

        void clear() {
            records.clear();
        }
    }
}
