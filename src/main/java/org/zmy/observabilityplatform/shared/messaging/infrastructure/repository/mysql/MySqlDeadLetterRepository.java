package org.zmy.observabilityplatform.shared.messaging.infrastructure.repository.mysql;

import io.r2dbc.spi.Row;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterMessage;
import org.zmy.observabilityplatform.shared.messaging.domain.repository.DeadLetterRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class MySqlDeadLetterRepository implements DeadLetterRepository {
    private static final String COLUMNS = "id, original_topic, message_key, payload, failure_reason, "
            + "source_partition, source_offset, failed_at, replayed_at";
    private final DatabaseClient databaseClient;

    public MySqlDeadLetterRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<DeadLetterMessage> saveIfAbsent(DeadLetterMessage message) {
        return databaseClient.sql("""
                        INSERT IGNORE INTO dead_letter_messages
                            (id, original_topic, message_key, payload, failure_reason, source_partition,
                             source_offset, failed_at, replayed_at)
                        VALUES (:id, :topic, :messageKey, :payload, :reason, :partition, :offset, :failedAt, NULL)
                        """)
                .bind("id", message.getId()).bind("topic", message.getOriginalTopic())
                .bind("messageKey", message.getMessageKey() == null ? "" : message.getMessageKey())
                .bind("payload", message.getPayload()).bind("reason", message.getFailureReason())
                .bind("partition", message.getSourcePartition()).bind("offset", message.getSourceOffset())
                .bind("failedAt", toDatabaseTime(message.getFailedAt()))
                .fetch().rowsUpdated().then(findById(message.getId()));
    }

    @Override
    public Mono<DeadLetterMessage> save(DeadLetterMessage message) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                        UPDATE dead_letter_messages SET replayed_at = :replayedAt WHERE id = :id
                        """).bind("id", message.getId());
        spec = message.getReplayedAt() == null
                ? spec.bindNull("replayedAt", LocalDateTime.class)
                : spec.bind("replayedAt", toDatabaseTime(message.getReplayedAt()));
        return spec.fetch().rowsUpdated().thenReturn(message);
    }

    @Override
    public Mono<DeadLetterMessage> findById(String id) {
        return databaseClient.sql("SELECT " + COLUMNS + " FROM dead_letter_messages WHERE id = :id")
                .bind("id", id).map((row, metadata) -> map(row)).one();
    }

    @Override
    public Flux<DeadLetterMessage> findAll(int limit) {
        return databaseClient.sql("SELECT " + COLUMNS
                        + " FROM dead_letter_messages ORDER BY failed_at DESC LIMIT :limit")
                .bind("limit", limit).map((row, metadata) -> map(row)).all();
    }

    private DeadLetterMessage map(Row row) {
        Integer partition = row.get("source_partition", Integer.class);
        Long offset = row.get("source_offset", Long.class);
        return DeadLetterMessage.restore(row.get("id", String.class), row.get("original_topic", String.class),
                blankToNull(row.get("message_key", String.class)), row.get("payload", String.class),
                row.get("failure_reason", String.class), partition == null ? 0 : partition,
                offset == null ? 0 : offset, toInstant(row, "failed_at"), toInstant(row, "replayed_at"));
    }

    private String blankToNull(String value) {
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
