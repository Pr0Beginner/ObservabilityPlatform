package org.zmy.observabilityplatform.logging.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.zmy.observabilityplatform.incident.application.service.AnomalyPolicyResolver;
import org.zmy.observabilityplatform.incident.application.service.ErrorThresholdDetector;
import org.zmy.observabilityplatform.incident.application.service.IncidentLifecycleService;
import org.zmy.observabilityplatform.incident.application.service.IncidentNotificationService;
import org.zmy.observabilityplatform.incident.application.service.RequestMetricRecorder;
import org.zmy.observabilityplatform.incident.domain.model.MetricKey;
import org.zmy.observabilityplatform.incident.infrastructure.repository.memory.InMemoryAnomalyPolicyRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.memory.InMemoryIncidentNotificationRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.memory.InMemoryIncidentRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.memory.InMemoryIncidentTraceLinkRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.memory.InMemoryMetricTraceSampleRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.memory.InMemoryMetricWindowRepository;
import org.zmy.observabilityplatform.logging.domain.model.RawLogBatch;
import org.zmy.observabilityplatform.logging.domain.model.LogEntry;
import org.zmy.observabilityplatform.logging.domain.model.RawLogRecord;
import org.zmy.observabilityplatform.logging.domain.service.LogEntryFactory;
import org.zmy.observabilityplatform.logging.domain.service.LogFingerprintGenerator;
import org.zmy.observabilityplatform.logging.domain.service.LogParserRegistry;
import org.zmy.observabilityplatform.logging.infrastructure.parser.JsonLogParser;
import org.zmy.observabilityplatform.logging.infrastructure.parser.JsonSensitiveDataProtector;
import org.zmy.observabilityplatform.logging.infrastructure.repository.memory.InMemoryLogRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogProcessingRecoveryTest {
    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");

    @Test
    void replayAfterIndexingRecoversMetricsExactlyOnce() {
        AtomicBoolean fail = new AtomicBoolean(true);
        var metrics = new InMemoryMetricWindowRepository() {
            @Override
            public Mono<Void> recordOnce(String id, List<MetricKey> keys, Instant window) {
                return Mono.defer(() -> id.startsWith("request:") && fail.getAndSet(false)
                        ? Mono.error(new IllegalStateException("temporary database outage"))
                        : super.recordOnce(id, keys, window));
            }
        };
        exerciseRecovery(metrics, new InMemoryMetricTraceSampleRepository());
    }

    @Test
    void replayAfterCounterCommitRecoversRemainingWorkWithoutDoubleCounting() {
        AtomicBoolean fail = new AtomicBoolean(true);
        var samples = new InMemoryMetricTraceSampleRepository() {
            @Override
            public Mono<Void> recordIfAbsent(MetricKey key, Instant window, String traceId) {
                return Mono.defer(() -> fail.getAndSet(false)
                        ? Mono.error(new IllegalStateException("temporary trace sample failure"))
                        : super.recordIfAbsent(key, window, traceId));
            }
        };
        exerciseRecovery(new InMemoryMetricWindowRepository(), samples);
    }

    private void exerciseRecovery(InMemoryMetricWindowRepository metrics, InMemoryMetricTraceSampleRepository samples) {
        exerciseRecovery(metrics, samples, new InMemoryLogRepository());
    }

    @Test
    void partialIndexSuccessDoesNotLosePreviouslyIndexedLogsOnReplay() {
        AtomicBoolean fail = new AtomicBoolean(true);
        var logs = new InMemoryLogRepository() {
            @Override
            public Mono<List<LogEntry>> saveAll(List<LogEntry> entries) {
                if (fail.getAndSet(false)) {
                    return super.saveAll(entries.subList(0, 1))
                            .then(Mono.error(new IllegalStateException("partial bulk failure")));
                }
                return super.saveAll(entries);
            }
        };
        exerciseRecovery(new InMemoryMetricWindowRepository(), new InMemoryMetricTraceSampleRepository(), logs);
    }

    private void exerciseRecovery(InMemoryMetricWindowRepository metrics, InMemoryMetricTraceSampleRepository samples,
                                  InMemoryLogRepository logs) {
        var incidents = new InMemoryIncidentRepository();
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var notifier = new IncidentNotificationService(new InMemoryIncidentNotificationRepository(), incidents,
                (incident, notification) -> Mono.empty(), clock, 120, 5);
        var detector = new ErrorThresholdDetector(incidents, metrics, new IncidentLifecycleService(incidents, notifier),
                new InMemoryIncidentTraceLinkRepository(), samples,
                new AnomalyPolicyResolver(new InMemoryAnomalyPolicyRepository()), clock);
        var mapper = new ObjectMapper();
        var factory = new LogEntryFactory(new LogParserRegistry(List.of(new JsonLogParser(mapper))),
                new JsonSensitiveDataProtector(mapper), new LogFingerprintGenerator());
        var service = new LogProcessingService(factory, logs, detector, new RequestMetricRecorder(metrics, samples, 5));
        var records = IntStream.range(0, 3).mapToObj(index -> RawLogRecord.capture("batch", index, NOW,
                "{\"level\":\"ERROR\",\"message\":\"database timeout\",\"operation\":\"POST /orders\","
                        + "\"spanKind\":\"SERVER\",\"statusCode\":500,\"durationMs\":10}",
                "JSON", "trace-" + index, Map.of())).toList();
        var batch = RawLogBatch.receive("batch", "orders", "test", NOW, records);

        assertThatThrownBy(() -> service.process(batch).block()).isInstanceOf(RuntimeException.class);
        service.process(batch).block();
        service.process(batch).block();

        assertThat(metrics.find(MetricKey.requestTotal("orders", "test", "POST /orders"), NOW).block().getCount())
                .isEqualTo(3);
        assertThat(metrics.find(MetricKey.requestFailure("orders", "test", "POST /orders"), NOW).block().getCount())
                .isEqualTo(3);
        assertThat(incidents.findAll().single().block().getErrorCount()).isEqualTo(3);
        assertThat(samples.findTraceId(MetricKey.requestTotal("orders", "test", "POST /orders"), NOW).block())
                .isNotBlank();
    }

    @Test
    void concurrentRedeliveriesIncrementAllCountersOnce() {
        var metrics = new InMemoryMetricWindowRepository();
        var keys = List.of(MetricKey.requestTotal("orders", "test", "route"),
                MetricKey.requestFailure("orders", "test", "route"));
        Flux.range(0, 30).flatMap(index -> metrics.recordOnce("one-observation", keys, NOW)
                .subscribeOn(Schedulers.parallel())).blockLast();
        keys.forEach(key -> assertThat(metrics.find(key, NOW).block().getCount()).isEqualTo(1));
    }
}
