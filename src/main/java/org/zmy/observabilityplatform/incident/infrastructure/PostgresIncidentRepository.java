package org.zmy.observabilityplatform.incident.infrastructure;

import io.r2dbc.spi.Row;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.incident.domain.Incident;
import org.zmy.observabilityplatform.incident.domain.IncidentRepository;
import org.zmy.observabilityplatform.incident.domain.IncidentSeverity;
import org.zmy.observabilityplatform.incident.domain.IncidentStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class PostgresIncidentRepository implements IncidentRepository {
    private static final String COLUMNS = "id, dedup_key, title, service, environment, fingerprint, severity, status, "
            + "started_at, updated_at, error_count, assignee, resolution";
    private final DatabaseClient databaseClient;

    public PostgresIncidentRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Incident> save(Incident incident) {
        return databaseClient.sql("""
                        INSERT INTO incidents (id, dedup_key, title, service, environment, fingerprint, severity, status,
                            started_at, updated_at, error_count, assignee, resolution)
                        VALUES (:id, :dedupKey, :title, :service, :environment, :fingerprint, :severity, :status,
                            :startedAt, :updatedAt, :errorCount, :assignee, :resolution)
                        ON CONFLICT (dedup_key) DO UPDATE SET
                            title = EXCLUDED.title, severity = EXCLUDED.severity, status = EXCLUDED.status,
                            updated_at = EXCLUDED.updated_at,
                            error_count = GREATEST(incidents.error_count, EXCLUDED.error_count),
                            assignee = EXCLUDED.assignee, resolution = EXCLUDED.resolution
                        RETURNING """ + COLUMNS)
                .bind("id", incident.id())
                .bind("dedupKey", incident.dedupKey())
                .bind("title", incident.title())
                .bind("service", incident.service())
                .bind("environment", incident.environment())
                .bind("fingerprint", incident.fingerprint())
                .bind("severity", incident.severity().name())
                .bind("status", incident.status().name())
                .bind("startedAt", incident.startedAt())
                .bind("updatedAt", incident.updatedAt())
                .bind("errorCount", incident.errorCount())
                .bind("assignee", incident.assignee())
                .bind("resolution", incident.resolution() == null ? "" : incident.resolution())
                .map((row, metadata) -> map(row))
                .one();
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
                row.get("started_at", Instant.class), row.get("updated_at", Instant.class),
                value(row.get("error_count", Long.class)), row.get("assignee", String.class),
                resolution == null || resolution.isBlank() ? null : resolution);
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }
}
