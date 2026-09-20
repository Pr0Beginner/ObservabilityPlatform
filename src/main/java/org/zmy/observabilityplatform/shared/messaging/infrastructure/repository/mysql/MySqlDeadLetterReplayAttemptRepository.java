package org.zmy.observabilityplatform.shared.messaging.infrastructure.repository.mysql;

import io.r2dbc.spi.Row;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterReplayAttempt;
import org.zmy.observabilityplatform.shared.messaging.domain.model.ReplayAttemptStatus;
import org.zmy.observabilityplatform.shared.messaging.domain.repository.DeadLetterReplayAttemptRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class MySqlDeadLetterReplayAttemptRepository implements DeadLetterReplayAttemptRepository {
    private static final String COLUMNS = "id, dead_letter_id, status, started_at, completed_at, failure_reason";
    private final DatabaseClient databaseClient;

    public MySqlDeadLetterReplayAttemptRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<DeadLetterReplayAttempt> save(DeadLetterReplayAttempt attempt) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                        INSERT INTO dead_letter_replay_attempts
                            (id, dead_letter_id, status, started_at, completed_at, failure_reason)
                        VALUES (:id, :deadLetterId, :status, :startedAt, :completedAt, :failureReason)
                        AS new
                        ON DUPLICATE KEY UPDATE status = new.status, completed_at = new.completed_at,
                            failure_reason = new.failure_reason
                        """)
                .bind("id", attempt.getId())
                .bind("deadLetterId", attempt.getDeadLetterId())
                .bind("status", attempt.getStatus().name())
                .bind("startedAt", toDatabaseTime(attempt.getStartedAt()));
        spec = bindNullable(spec, "completedAt", toDatabaseTime(attempt.getCompletedAt()), LocalDateTime.class);
        spec = bindNullable(spec, "failureReason", attempt.getFailureReason(), String.class);
        return spec.fetch().rowsUpdated().thenReturn(attempt);
    }

    @Override
    public Flux<DeadLetterReplayAttempt> findByDeadLetterId(String deadLetterId, int limit) {
        return databaseClient.sql("SELECT " + COLUMNS
                        + " FROM dead_letter_replay_attempts WHERE dead_letter_id = :deadLetterId"
                        + " ORDER BY started_at DESC, id ASC LIMIT :limit")
                .bind("deadLetterId", deadLetterId)
                .bind("limit", limit)
                .map((row, metadata) -> map(row)).all();
    }

    private DeadLetterReplayAttempt map(Row row) {
        return DeadLetterReplayAttempt.restore(row.get("id", String.class),
                row.get("dead_letter_id", String.class),
                ReplayAttemptStatus.valueOf(row.get("status", String.class)),
                toInstant(row, "started_at"), toInstant(row, "completed_at"),
                row.get("failure_reason", String.class));
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
