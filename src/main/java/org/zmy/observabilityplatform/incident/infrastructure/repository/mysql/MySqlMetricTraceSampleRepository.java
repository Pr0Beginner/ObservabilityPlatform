package org.zmy.observabilityplatform.incident.infrastructure.repository.mysql;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.incident.domain.model.MetricKey;
import org.zmy.observabilityplatform.incident.domain.repository.MetricTraceSampleRepository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class MySqlMetricTraceSampleRepository implements MetricTraceSampleRepository {
    private final DatabaseClient databaseClient;

    public MySqlMetricTraceSampleRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Void> recordIfAbsent(MetricKey key, Instant windowStart, String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return Mono.empty();
        }
        return databaseClient.sql("""
                        INSERT IGNORE INTO metric_trace_samples (metric_key, window_start, trace_id)
                        VALUES (:metricKey, :windowStart, :traceId)
                        """)
                .bind("metricKey", key.value())
                .bind("windowStart", toDatabaseTime(windowStart))
                .bind("traceId", traceId)
                .fetch().rowsUpdated().then();
    }

    @Override
    public Mono<String> findTraceId(MetricKey key, Instant windowStart) {
        return databaseClient.sql("""
                        SELECT trace_id FROM metric_trace_samples
                        WHERE metric_key = :metricKey AND window_start = :windowStart
                        """)
                .bind("metricKey", key.value())
                .bind("windowStart", toDatabaseTime(windowStart))
                .map((row, metadata) -> row.get("trace_id", String.class)).one();
    }

    @Override
    public Mono<Long> deleteBefore(Instant cutoff, int limit) {
        if (limit < 1) {
            return Mono.error(new IllegalArgumentException("limit must be positive"));
        }
        return databaseClient.sql("""
                        DELETE FROM metric_trace_samples
                        WHERE window_start < :cutoff
                        ORDER BY window_start ASC
                        LIMIT :limit
                        """)
                .bind("cutoff", toDatabaseTime(cutoff))
                .bind("limit", limit)
                .fetch().rowsUpdated();
    }

    private LocalDateTime toDatabaseTime(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
