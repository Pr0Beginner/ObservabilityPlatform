package org.zmy.observabilityplatform.incident.infrastructure.repository.mysql;

import io.r2dbc.spi.Row;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.incident.domain.exception.IncidentVersionConflictException;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicyReference;
import org.zmy.observabilityplatform.incident.domain.model.Incident;
import org.zmy.observabilityplatform.incident.domain.model.IncidentSeverity;
import org.zmy.observabilityplatform.incident.domain.model.IncidentStatus;
import org.zmy.observabilityplatform.incident.domain.model.IncidentType;
import org.zmy.observabilityplatform.incident.domain.repository.IncidentRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class MySqlIncidentRepository implements IncidentRepository {
    private static final String COLUMNS = "id, dedup_key, title, service, environment, fingerprint, incident_type, "
            + "operation_name, dimension_value, severity, status, started_at, updated_at, error_count, assignee, "
            + "resolution, current_value, baseline_value, recovered_at, healthy_window_count, last_observed_window, "
            + "policy_id, policy_version, version";
    private final DatabaseClient databaseClient;

    public MySqlIncidentRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Incident> save(Incident incident) {
        return incident.getVersion() == 0 ? insert(incident) : update(incident);
    }

    private Mono<Incident> insert(Incident incident) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                        INSERT INTO incidents (id, dedup_key, title, service, environment, fingerprint, incident_type,
                            operation_name, dimension_value, severity, status, started_at, updated_at, error_count,
                            assignee, resolution, current_value, baseline_value, recovered_at, healthy_window_count,
                            last_observed_window, policy_id, policy_version, version)
                        VALUES (:id, :dedupKey, :title, :service, :environment, :fingerprint, :incidentType,
                            :operation, :dimension, :severity, :status, :startedAt, :updatedAt, :errorCount,
                            :assignee, :resolution, :currentValue, :baselineValue, :recoveredAt, :healthyWindowCount,
                            :lastObservedWindow, :policyId, :policyVersion, 1)
                        ON DUPLICATE KEY UPDATE id = id
                        """)
                .bind("id", incident.getId())
                .bind("dedupKey", incident.getDedupKey());
        return bindIncident(spec, incident).fetch().rowsUpdated()
                .then(findByDedupKey(incident.getDedupKey()));
    }

    private Mono<Incident> update(Incident incident) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                        UPDATE incidents
                        SET title = :title, service = :service, environment = :environment,
                            fingerprint = :fingerprint, incident_type = :incidentType,
                            operation_name = :operation, dimension_value = :dimension,
                            severity = :severity, status = :status, started_at = :startedAt, updated_at = :updatedAt,
                            error_count = :errorCount, assignee = :assignee, resolution = :resolution,
                            current_value = :currentValue, baseline_value = :baselineValue,
                            recovered_at = :recoveredAt, healthy_window_count = :healthyWindowCount,
                            last_observed_window = :lastObservedWindow, policy_id = :policyId,
                            policy_version = :policyVersion, version = version + 1
                        WHERE id = :id AND version = :version
                        """)
                .bind("id", incident.getId())
                .bind("version", incident.getVersion());
        return bindIncident(spec, incident).fetch().rowsUpdated()
                .flatMap(rows -> rows == 1
                        ? Mono.just(incident.persistedAtVersion(incident.getVersion() + 1))
                        : Mono.error(new IncidentVersionConflictException(
                                incident.getId(), incident.getVersion())));
    }

    private DatabaseClient.GenericExecuteSpec bindIncident(DatabaseClient.GenericExecuteSpec spec,
                                                            Incident incident) {
        spec = spec
                .bind("title", incident.getTitle())
                .bind("service", incident.getService())
                .bind("environment", incident.getEnvironment())
                .bind("fingerprint", text(incident.getFingerprint()))
                .bind("incidentType", incident.getType().name())
                .bind("operation", text(incident.getOperation()))
                .bind("dimension", text(incident.getDimension()))
                .bind("severity", incident.getSeverity().name())
                .bind("status", incident.getStatus().name())
                .bind("startedAt", toDatabaseTime(incident.getStartedAt()))
                .bind("updatedAt", toDatabaseTime(incident.getUpdatedAt()))
                .bind("errorCount", incident.getErrorCount())
                .bind("assignee", incident.getAssignee())
                .bind("resolution", text(incident.getResolution()))
                .bind("policyId", incident.getPolicyReference().getPolicyId())
                .bind("policyVersion", incident.getPolicyReference().getPolicyVersion())
                .bind("healthyWindowCount", incident.getHealthyWindowCount());
        spec = bindNullable(spec, "currentValue", incident.getCurrentValue(), Double.class);
        spec = bindNullable(spec, "baselineValue", incident.getBaselineValue(), Double.class);
        spec = bindNullable(spec, "recoveredAt", toDatabaseTime(incident.getRecoveredAt()), LocalDateTime.class);
        spec = bindNullable(spec, "lastObservedWindow", toDatabaseTime(incident.getLastObservedWindow()), LocalDateTime.class);
        return spec;
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
        return Incident.restore(row.get("id", String.class), row.get("dedup_key", String.class),
                row.get("title", String.class), row.get("service", String.class),
                row.get("environment", String.class), row.get("fingerprint", String.class),
                IncidentType.valueOf(row.get("incident_type", String.class)),
                row.get("operation_name", String.class), row.get("dimension_value", String.class),
                IncidentSeverity.valueOf(row.get("severity", String.class)),
                IncidentStatus.valueOf(row.get("status", String.class)),
                toInstant(row, "started_at"), toInstant(row, "updated_at"),
                value(row.get("error_count", Long.class)), row.get("assignee", String.class),
                resolution == null || resolution.isBlank() ? null : resolution,
                row.get("current_value", Double.class), row.get("baseline_value", Double.class),
                toInstant(row, "recovered_at"), integer(row.get("healthy_window_count", Integer.class)),
                toInstant(row, "last_observed_window"), new AnomalyPolicyReference(
                        row.get("policy_id", String.class), value(row.get("policy_version", Long.class))),
                value(row.get("version", Long.class)));
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }

    private int integer(Integer value) {
        return value == null ? 0 : value;
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private <T> DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec,
                                                                String name, T value, Class<T> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    private LocalDateTime toDatabaseTime(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private Instant toInstant(Row row, String column) {
        LocalDateTime value = row.get(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
