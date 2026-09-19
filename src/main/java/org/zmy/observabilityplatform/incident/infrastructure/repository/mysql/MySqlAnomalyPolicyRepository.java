package org.zmy.observabilityplatform.incident.infrastructure.repository.mysql;

import io.r2dbc.spi.Row;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.incident.domain.exception.AnomalyPolicyConflictException;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyDetectionSettings;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicy;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicyScope;
import org.zmy.observabilityplatform.incident.domain.repository.AnomalyPolicyRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class MySqlAnomalyPolicyRepository implements AnomalyPolicyRepository {
    private static final String COLUMNS = "id, name, scope, scope_key, service, environment, operation_name, "
            + "enabled, error_threshold, minimum_requests, minimum_error_code_count, request_spike_ratio, "
            + "request_drop_ratio, failure_rate_threshold, error_code_rate_threshold, baseline_multiplier, "
            + "comparison_period_minutes, recovery_windows, version, created_at, updated_at";

    private final DatabaseClient databaseClient;

    public MySqlAnomalyPolicyRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<AnomalyPolicy> create(AnomalyPolicy policy) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                        INSERT INTO anomaly_policies
                            (id, name, scope, scope_key, service, environment, operation_name, enabled,
                             error_threshold, minimum_requests, minimum_error_code_count, request_spike_ratio,
                             request_drop_ratio, failure_rate_threshold, error_code_rate_threshold,
                             baseline_multiplier, comparison_period_minutes, recovery_windows, version,
                             created_at, updated_at)
                        VALUES
                            (:id, :name, :scope, :scopeKey, :service, :environment, :operation, :enabled,
                             :errorThreshold, :minimumRequests, :minimumErrorCodeCount, :requestSpikeRatio,
                             :requestDropRatio, :failureRateThreshold, :errorCodeRateThreshold,
                             :baselineMultiplier, :comparisonPeriodMinutes, :recoveryWindows, :version,
                             :createdAt, :updatedAt)
                        """);
        return bind(spec, policy).fetch().rowsUpdated()
                .thenReturn(policy)
                .onErrorMap(DataIntegrityViolationException.class,
                        error -> AnomalyPolicyConflictException.duplicateScope());
    }

    @Override
    public Mono<AnomalyPolicy> update(AnomalyPolicy policy) {
        long expectedVersion = policy.getVersion() - 1;
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                        UPDATE anomaly_policies
                        SET name = :name, scope = :scope, scope_key = :scopeKey,
                            service = :service, environment = :environment, operation_name = :operation,
                            enabled = :enabled, error_threshold = :errorThreshold,
                            minimum_requests = :minimumRequests,
                            minimum_error_code_count = :minimumErrorCodeCount,
                            request_spike_ratio = :requestSpikeRatio,
                            request_drop_ratio = :requestDropRatio,
                            failure_rate_threshold = :failureRateThreshold,
                            error_code_rate_threshold = :errorCodeRateThreshold,
                            baseline_multiplier = :baselineMultiplier,
                            comparison_period_minutes = :comparisonPeriodMinutes,
                            recovery_windows = :recoveryWindows, version = :version,
                            created_at = :createdAt, updated_at = :updatedAt
                        WHERE id = :id AND version = :expectedVersion
                        """).bind("expectedVersion", expectedVersion);
        return bind(spec, policy).fetch().rowsUpdated()
                .flatMap(rows -> rows == 1
                        ? Mono.just(policy)
                        : Mono.error(AnomalyPolicyConflictException.staleVersion(
                                policy.getId(), expectedVersion)))
                .onErrorMap(DataIntegrityViolationException.class,
                        error -> AnomalyPolicyConflictException.duplicateScope());
    }

    @Override
    public Mono<AnomalyPolicy> findById(String id) {
        return databaseClient.sql("SELECT " + COLUMNS + " FROM anomaly_policies WHERE id = :id")
                .bind("id", id).map((row, metadata) -> map(row)).one();
    }

    @Override
    public Mono<AnomalyPolicy> findByScope(AnomalyPolicyScope scope, String service,
                                           String environment, String operation) {
        return databaseClient.sql("SELECT " + COLUMNS + " FROM anomaly_policies WHERE scope_key = :scopeKey")
                .bind("scopeKey", AnomalyPolicy.scopeKeyOf(scope, service, environment, operation))
                .map((row, metadata) -> map(row)).one();
    }

    @Override
    public Flux<AnomalyPolicy> findAll() {
        return databaseClient.sql("SELECT " + COLUMNS + " FROM anomaly_policies ORDER BY scope, name")
                .map((row, metadata) -> map(row)).all();
    }

    private DatabaseClient.GenericExecuteSpec bind(DatabaseClient.GenericExecuteSpec spec, AnomalyPolicy policy) {
        AnomalyDetectionSettings settings = policy.getSettings();
        return spec.bind("id", policy.getId())
                .bind("name", policy.getName())
                .bind("scope", policy.getScope().name())
                .bind("scopeKey", policy.scopeKey())
                .bind("service", text(policy.getService()))
                .bind("environment", text(policy.getEnvironment()))
                .bind("operation", text(policy.getOperation()))
                .bind("enabled", policy.isEnabled())
                .bind("errorThreshold", settings.getErrorThreshold())
                .bind("minimumRequests", settings.getMinimumRequests())
                .bind("minimumErrorCodeCount", settings.getMinimumErrorCodeCount())
                .bind("requestSpikeRatio", settings.getRequestSpikeRatio())
                .bind("requestDropRatio", settings.getRequestDropRatio())
                .bind("failureRateThreshold", settings.getFailureRateThreshold())
                .bind("errorCodeRateThreshold", settings.getErrorCodeRateThreshold())
                .bind("baselineMultiplier", settings.getBaselineMultiplier())
                .bind("comparisonPeriodMinutes", settings.getComparisonPeriodMinutes())
                .bind("recoveryWindows", settings.getRecoveryWindows())
                .bind("version", policy.getVersion())
                .bind("createdAt", toDatabaseTime(policy.getCreatedAt()))
                .bind("updatedAt", toDatabaseTime(policy.getUpdatedAt()));
    }

    private AnomalyPolicy map(Row row) {
        AnomalyDetectionSettings settings = new AnomalyDetectionSettings(
                integer(row, "error_threshold"), integer(row, "minimum_requests"),
                integer(row, "minimum_error_code_count"), number(row, "request_spike_ratio"),
                number(row, "request_drop_ratio"), number(row, "failure_rate_threshold"),
                number(row, "error_code_rate_threshold"), number(row, "baseline_multiplier"),
                integer(row, "comparison_period_minutes"), integer(row, "recovery_windows"));
        return AnomalyPolicy.restore(row.get("id", String.class), row.get("name", String.class),
                AnomalyPolicyScope.valueOf(row.get("scope", String.class)),
                emptyToNull(row.get("service", String.class)), emptyToNull(row.get("environment", String.class)),
                emptyToNull(row.get("operation_name", String.class)), Boolean.TRUE.equals(row.get("enabled", Boolean.class)),
                settings, longValue(row, "version"), toInstant(row, "created_at"), toInstant(row, "updated_at"));
    }

    private int integer(Row row, String column) {
        Integer value = row.get(column, Integer.class);
        return value == null ? 0 : value;
    }

    private long longValue(Row row, String column) {
        Long value = row.get(column, Long.class);
        return value == null ? 0 : value;
    }

    private double number(Row row, String column) {
        Double value = row.get(column, Double.class);
        return value == null ? 0 : value;
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private LocalDateTime toDatabaseTime(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private Instant toInstant(Row row, String column) {
        LocalDateTime value = row.get(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
