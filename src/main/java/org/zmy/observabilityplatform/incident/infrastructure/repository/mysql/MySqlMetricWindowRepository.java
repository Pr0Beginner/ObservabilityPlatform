package org.zmy.observabilityplatform.incident.infrastructure.repository.mysql;

import io.r2dbc.spi.Row;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.zmy.observabilityplatform.incident.domain.model.MetricKey;
import org.zmy.observabilityplatform.incident.domain.model.MetricType;
import org.zmy.observabilityplatform.incident.domain.model.MetricWindow;
import org.zmy.observabilityplatform.incident.domain.repository.MetricWindowRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class MySqlMetricWindowRepository implements MetricWindowRepository {
    private static final String COLUMNS = "metric_type, service, environment, operation_name, dimension_value, "
            + "window_start, metric_count";

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactions;

    public MySqlMetricWindowRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
        this.transactions = TransactionalOperator.create(new R2dbcTransactionManager(databaseClient.getConnectionFactory()));
    }

    @Override
    public Mono<Void> recordOnce(String observationId, List<MetricKey> keys, Instant windowStart) {
        return Mono.defer(() -> {
            if (observationId == null || observationId.isBlank() || observationId.length() > 255
                    || keys == null || keys.isEmpty() || windowStart == null) {
                return Mono.error(new IllegalArgumentException("observation ID, metric keys and window are required"));
            }
            List<MetricKey> validKeys = List.copyOf(keys);
            Mono<Void> work = databaseClient.sql("""
                            INSERT INTO metric_observations (observation_id, window_start)
                            VALUES (:id, :windowStart)
                            """)
                    .bind("id", observationId).bind("windowStart", toDatabaseTime(windowStart))
                    .fetch().rowsUpdated().map(rows -> true)
                    .onErrorResume(DuplicateKeyException.class, error -> Mono.just(false))
                    .flatMap(created -> created ? incrementAll(validKeys, windowStart) : Mono.empty());
            return transactions.transactional(work);
        });
    }

    @Override
    public Mono<MetricWindow> increment(MetricKey key, Instant windowStart, long delta) {
        if (delta < 1) {
            return Mono.error(new IllegalArgumentException("delta must be positive"));
        }
        return databaseClient.sql("""
                        INSERT INTO metric_windows
                            (metric_key, metric_type, service, environment, operation_name, dimension_value,
                             window_start, metric_count)
                        VALUES (:metricKey, :metricType, :service, :environment, :operation, :dimension,
                                :windowStart, :delta)
                        AS new
                        ON DUPLICATE KEY UPDATE metric_count = metric_count + new.metric_count
                        """)
                .bind("metricKey", key.value())
                .bind("metricType", key.getType().name())
                .bind("service", key.getService())
                .bind("environment", key.getEnvironment())
                .bind("operation", key.getOperation())
                .bind("dimension", key.getDimension())
                .bind("windowStart", toDatabaseTime(windowStart))
                .bind("delta", delta)
                .fetch().rowsUpdated()
                .then(find(key, windowStart));
    }

    @Override
    public Mono<Void> incrementAll(List<MetricKey> keys, Instant windowStart) {
        if (keys == null || keys.isEmpty()) {
            return Mono.error(new IllegalArgumentException("keys must not be empty"));
        }
        StringBuilder sql = new StringBuilder("""
                INSERT INTO metric_windows
                    (metric_key, metric_type, service, environment, operation_name, dimension_value,
                     window_start, metric_count) VALUES
                """);
        for (int index = 0; index < keys.size(); index++) {
            if (index > 0) {
                sql.append(",");
            }
            sql.append("(:key").append(index).append(", :type").append(index)
                    .append(", :service").append(index).append(", :environment").append(index)
                    .append(", :operation").append(index).append(", :dimension").append(index)
                    .append(", :windowStart, 1)");
        }
        sql.append(" AS new ON DUPLICATE KEY UPDATE metric_count = metric_count + new.metric_count");
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql.toString())
                .bind("windowStart", toDatabaseTime(windowStart));
        for (int index = 0; index < keys.size(); index++) {
            MetricKey key = keys.get(index);
            spec = spec.bind("key" + index, key.value())
                    .bind("type" + index, key.getType().name())
                    .bind("service" + index, key.getService())
                    .bind("environment" + index, key.getEnvironment())
                    .bind("operation" + index, key.getOperation())
                    .bind("dimension" + index, key.getDimension());
        }
        return spec.fetch().rowsUpdated().then();
    }

    @Override
    public Mono<MetricWindow> find(MetricKey key, Instant windowStart) {
        return databaseClient.sql("SELECT " + COLUMNS
                        + " FROM metric_windows WHERE metric_key = :metricKey AND window_start = :windowStart")
                .bind("metricKey", key.value())
                .bind("windowStart", toDatabaseTime(windowStart))
                .map((row, metadata) -> map(row))
                .one();
    }

    @Override
    public Flux<MetricWindow> findByWindow(Instant windowStart) {
        return databaseClient.sql("SELECT " + COLUMNS + " FROM metric_windows WHERE window_start = :windowStart")
                .bind("windowStart", toDatabaseTime(windowStart))
                .map((row, metadata) -> map(row))
                .all();
    }

    @Override
    public Mono<Long> deleteBefore(Instant cutoff, int limit) {
        if (limit < 1) {
            return Mono.error(new IllegalArgumentException("limit must be positive"));
        }
        return databaseClient.sql("""
                        DELETE FROM metric_windows
                        WHERE window_start < :cutoff
                        ORDER BY window_start ASC
                        LIMIT :limit
                        """)
                .bind("cutoff", toDatabaseTime(cutoff))
                .bind("limit", limit)
                .fetch().rowsUpdated();
    }

    private MetricWindow map(Row row) {
        MetricType type = MetricType.valueOf(row.get("metric_type", String.class));
        String service = row.get("service", String.class);
        String environment = row.get("environment", String.class);
        String operation = row.get("operation_name", String.class);
        String dimension = row.get("dimension_value", String.class);
        MetricKey key = switch (type) {
            case REQUEST_TOTAL -> MetricKey.requestTotal(service, environment, operation);
            case REQUEST_FAILURE -> MetricKey.requestFailure(service, environment, operation);
            case ERROR_CODE -> MetricKey.errorCode(service, environment, operation, dimension);
            case ERROR_FINGERPRINT -> MetricKey.errorFingerprint(service, environment, dimension);
        };
        Long count = row.get("metric_count", Long.class);
        return new MetricWindow(key, toInstant(row, "window_start"), count == null ? 0 : count);
    }

    private LocalDateTime toDatabaseTime(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private Instant toInstant(Row row, String column) {
        LocalDateTime value = row.get(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
