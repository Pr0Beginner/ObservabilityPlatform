package org.zmy.observabilityplatform.incident.infrastructure.repository.mysql;

import io.r2dbc.spi.Row;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.incident.domain.model.Incident;
import org.zmy.observabilityplatform.incident.domain.model.IncidentSeverity;
import org.zmy.observabilityplatform.incident.domain.model.IncidentStatus;
import org.zmy.observabilityplatform.incident.domain.repository.IncidentRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class MySqlIncidentRepository implements IncidentRepository {
    private static final String COLUMNS = "id, dedup_key, title, service, environment, fingerprint, severity, status, "
            + "started_at, updated_at, error_count, assignee, resolution";
    private final DatabaseClient databaseClient;

    public MySqlIncidentRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Incident> save(Incident incident) {
        return databaseClient.sql("""
                        INSERT INTO incidents (id, dedup_key, title, service, environment, fingerprint, severity, status,
                            started_at, updated_at, error_count, assignee, resolution)
                        VALUES (:id, :dedupKey, :title, :service, :environment, :fingerprint, :severity, :status,
                            :startedAt, :updatedAt, :errorCount, :assignee, :resolution)
                        AS new
                        ON DUPLICATE KEY UPDATE
                            title = new.title, severity = new.severity, status = new.status,
                            updated_at = new.updated_at,
                            error_count = GREATEST(error_count, new.error_count),
                            assignee = new.assignee, resolution = new.resolution
                        """)
                .bind("id", incident.id())
                .bind("dedupKey", incident.dedupKey())
                .bind("title", incident.title())
                .bind("service", incident.service())
                .bind("environment", incident.environment())
                .bind("fingerprint", incident.fingerprint())
                .bind("severity", incident.severity().name())
                .bind("status", incident.status().name())
                .bind("startedAt", toDatabaseTime(incident.startedAt()))
                .bind("updatedAt", toDatabaseTime(incident.updatedAt()))
                .bind("errorCount", incident.errorCount())
                .bind("assignee", incident.assignee())
                .bind("resolution", incident.resolution() == null ? "" : incident.resolution())
                .fetch()
                .rowsUpdated()
                .then(findByDedupKey(incident.dedupKey()));
    }

    @Override
    public Mono<Incident> findById(String id) {
        return databaseClient.sql("SELECT " + COLUMNS + " FROM incidents WHERE id = :id")
                .bind("id", id).map((row, metadata) -> map(row)).one();
    }

    @Override
    public Mono<Incident> findByDedupKey(String dedupKey) {
        return databaseClient.sql("SELECT " + COLUMNS + " FROM incidents WHERE dedup_key = :dedupKey")
                .bind("dedupKey", dedupKey).map((row, metadata) -> map(row)).one();
    }

    @Override
    public Flux<Incident> findAll() {
        return databaseClient.sql("SELECT " + COLUMNS + " FROM incidents ORDER BY started_at DESC LIMIT 200")
                .map((row, metadata) -> map(row)).all();
    }

    private Incident map(Row row) {
        String resolution = row.get("resolution", String.class);
        return new Incident(row.get("id", String.class), row.get("dedup_key", String.class),
                row.get("title", String.class), row.get("service", String.class),
                row.get("environment", String.class), row.get("fingerprint", String.class),
                IncidentSeverity.valueOf(row.get("severity", String.class)),
                IncidentStatus.valueOf(row.get("status", String.class)),
                toInstant(row, "started_at"), toInstant(row, "updated_at"),
                value(row.get("error_count", Long.class)), row.get("assignee", String.class),
                resolution == null || resolution.isBlank() ? null : resolution);
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }

    private LocalDateTime toDatabaseTime(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private Instant toInstant(Row row, String column) {
        LocalDateTime value = row.get(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
