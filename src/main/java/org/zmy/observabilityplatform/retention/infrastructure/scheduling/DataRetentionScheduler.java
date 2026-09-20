package org.zmy.observabilityplatform.retention.infrastructure.scheduling;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.zmy.observabilityplatform.retention.application.dto.RetentionCleanupResult;
import org.zmy.observabilityplatform.retention.application.service.DataRetentionService;
import org.zmy.observabilityplatform.retention.infrastructure.configuration.DataRetentionProperties;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.retention.enabled", havingValue = "true", matchIfMissing = true)
public class DataRetentionScheduler {
    private final DataRetentionService service;
    private final DataRetentionProperties properties;
    private final MeterRegistry meterRegistry;
    private final AtomicBoolean running = new AtomicBoolean();

    public DataRetentionScheduler(DataRetentionService service, DataRetentionProperties properties,
                                  MeterRegistry meterRegistry) {
        this.service = service;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(cron = "${app.retention.cleanup-cron:0 15 * * * *}")
    public void cleanupExpiredData() {
        if (!running.compareAndSet(false, true)) {
            Counter.builder("observability.retention.runs")
                    .tag("target", "all").tag("outcome", "skipped")
                    .register(meterRegistry).increment();
            log.warn("Skipped retention cleanup because the previous local run is still active");
            return;
        }
        service.cleanup(properties.toPolicy()).collectList().subscribe(
                results -> {
                    running.set(false);
                    results.forEach(this::record);
                },
                error -> {
                    Counter.builder("observability.retention.runs")
                            .tag("target", "all").tag("outcome", "failed")
                            .register(meterRegistry).increment();
                    running.set(false);
                    log.error("Unexpected data retention failure", error);
                });
    }

    private void record(RetentionCleanupResult result) {
        String outcome = result.isSuccessful() ? "succeeded" : "failed";
        Counter.builder("observability.retention.runs")
                .tag("target", result.getTarget()).tag("outcome", outcome)
                .register(meterRegistry).increment();
        Counter.builder("observability.retention.deleted")
                .tag("target", result.getTarget())
                .register(meterRegistry).increment(result.getDeletedRecords());
        Timer.builder("observability.retention.duration")
                .tag("target", result.getTarget()).tag("outcome", outcome)
                .register(meterRegistry).record(result.getDuration());
        if (result.isSuccessful()) {
            log.info("Retention cleanup completed: target={}, deleted={}, durationMs={}",
                    result.getTarget(), result.getDeletedRecords(), result.getDuration().toMillis());
        } else {
            log.error("Retention cleanup failed: target={}, deleted={}, durationMs={}, reason={}",
                    result.getTarget(), result.getDeletedRecords(), result.getDuration().toMillis(),
                    result.getFailure());
        }
    }
}
