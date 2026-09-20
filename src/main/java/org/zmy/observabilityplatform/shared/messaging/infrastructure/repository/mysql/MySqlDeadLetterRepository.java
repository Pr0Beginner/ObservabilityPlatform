package org.zmy.observabilityplatform.shared.messaging.infrastructure.repository.mysql;

import io.r2dbc.spi.Row;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.shared.application.query.PageResult;
import org.zmy.observabilityplatform.shared.messaging.application.query.DeadLetterQueryRepository;
import org.zmy.observabilityplatform.shared.messaging.application.query.DeadLetterSearchQuery;
import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterMessage;
import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterStatus;
import org.zmy.observabilityplatform.shared.messaging.domain.repository.DeadLetterRepository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class MySqlDeadLetterRepository implements DeadLetterRepository, DeadLetterQueryRepository {
    private static final String COLUMNS = "id, original_topic, message_key, payload, failure_type, failure_reason, "
            + "source_partition, source_offset, failed_at, replayed_at";
    private final DatabaseClient databaseClient;

    public MySqlDeadLetterRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<DeadLetterMessage> saveIfAbsent(DeadLetterMessage message) {
        return databaseClient.sql("""
                        INSERT IGNORE INTO dead_letter_messages
                            (id, original_topic, message_key, payload, failure_type, failure_reason, source_partition,
                             source_offset, failed_at, replayed_at)
                        VALUES (:id, :topic, :messageKey, :payload, :failureType, :reason,
                            :partition, :offset, :failedAt, NULL)
                        """)
                .bind("id", message.getId()).bind("topic", message.getOriginalTopic())
                .bind("messageKey", message.getMessageKey() == null ? "" : message.getMessageKey())
                .bind("payload", message.getPayload()).bind("failureType", message.getFailureType())
                .bind("reason", message.getFailureReason())
                .bind("partition", message.getSourcePartition()).bind("offset", message.getSourceOffset())
                .bind("failedAt", toDatabaseTime(message.getFailedAt()))
                .fetch().rowsUpdated().then(findById(message.getId()));
    }

    @Override
    public Mono<DeadLetterMessage> save(DeadLetterMessage message) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                        UPDATE dead_letter_messages
                        SET replayed_at = :replayedAt, replay_owner = '', replay_lease_until = NULL
                        WHERE id = :id
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
    public Mono<DeadLetterMessage> claimForReplay(String id, String owner, Instant now, Instant leaseUntil) {
        return databaseClient.sql("""
                        UPDATE dead_letter_messages
                        SET replay_owner = :owner, replay_lease_until = :leaseUntil
                        WHERE id = :id AND replayed_at IS NULL
                          AND (replay_owner = '' OR replay_lease_until <= :now)
                        """)
                .bind("owner", owner)
                .bind("leaseUntil", toDatabaseTime(leaseUntil))
                .bind("id", id)
                .bind("now", toDatabaseTime(now))
                .fetch().rowsUpdated()
                .filter(rows -> rows == 1)
                .flatMap(rows -> findById(id));
    }

    @Override
    public Mono<DeadLetterMessage> completeReplay(String id, String owner, Instant replayedAt) {
        return databaseClient.sql("""
                        UPDATE dead_letter_messages
                        SET replayed_at = :replayedAt, replay_owner = '', replay_lease_until = NULL
                        WHERE id = :id AND replayed_at IS NULL AND replay_owner = :owner
                        """)
                .bind("replayedAt", toDatabaseTime(replayedAt))
                .bind("id", id)
                .bind("owner", owner)
                .fetch().rowsUpdated()
                .filter(rows -> rows == 1)
                .flatMap(rows -> findById(id));
    }

    @Override
    public Mono<Boolean> releaseReplay(String id, String owner) {
        return databaseClient.sql("""
                        UPDATE dead_letter_messages
                        SET replay_owner = '', replay_lease_until = NULL
                        WHERE id = :id AND replayed_at IS NULL AND replay_owner = :owner
                        """)
                .bind("id", id)
                .bind("owner", owner)
                .fetch().rowsUpdated()
                .map(rows -> rows == 1);
    }

    @Override
    public Mono<PageResult<DeadLetterMessage>> search(DeadLetterSearchQuery query) {
        String predicate = predicate(query);
        DatabaseClient.GenericExecuteSpec countSpec = bindFilters(databaseClient.sql(
                "SELECT COUNT(*) AS total FROM dead_letter_messages" + predicate), query);
        Mono<Long> total = countSpec.map((row, metadata) -> number(row.get("total", Long.class))).one();

        DatabaseClient.GenericExecuteSpec pageSpec = bindFilters(databaseClient.sql("SELECT " + COLUMNS
                + " FROM dead_letter_messages" + predicate
                + " ORDER BY failed_at DESC, id ASC LIMIT :limit OFFSET :offset"), query)
                .bind("limit", query.getSize())
                .bind("offset", query.offset());
        Mono<List<DeadLetterMessage>> items = pageSpec.map((row, metadata) -> map(row)).all().collectList();
        return Mono.zip(items, total)
                .map(tuple -> PageResult.of(tuple.getT1(), query.getPage(), query.getSize(), tuple.getT2()));
    }

    @Override
    public Mono<Long> deleteReplayedBefore(Instant cutoff, int limit) {
        return deleteBefore("replayed_at IS NOT NULL AND replayed_at < :cutoff",
                "replayed_at", cutoff, limit);
    }

    @Override
    public Mono<Long> deleteUnreplayedBefore(Instant cutoff, Instant now, int limit) {
        if (limit < 1) {
            return Mono.error(new IllegalArgumentException("limit must be positive"));
        }
        return databaseClient.sql("""
                        DELETE FROM dead_letter_messages
                        WHERE replayed_at IS NULL AND failed_at < :cutoff
                          AND (replay_owner = '' OR replay_lease_until <= :now)
                        ORDER BY failed_at ASC
                        LIMIT :limit
                        """)
                .bind("cutoff", toDatabaseTime(cutoff))
                .bind("now", toDatabaseTime(now))
                .bind("limit", limit)
                .fetch().rowsUpdated();
    }

    private Mono<Long> deleteBefore(String predicate, String orderColumn, Instant cutoff, int limit) {
        if (limit < 1) {
            return Mono.error(new IllegalArgumentException("limit must be positive"));
        }
        return databaseClient.sql("DELETE FROM dead_letter_messages WHERE " + predicate
                        + " ORDER BY " + orderColumn + " ASC LIMIT :limit")
                .bind("cutoff", toDatabaseTime(cutoff))
                .bind("limit", limit)
                .fetch().rowsUpdated();
    }

    private DeadLetterMessage map(Row row) {
        Integer partition = row.get("source_partition", Integer.class);
        Long offset = row.get("source_offset", Long.class);
        return DeadLetterMessage.restore(row.get("id", String.class), row.get("original_topic", String.class),
                blankToNull(row.get("message_key", String.class)), row.get("payload", String.class),
                row.get("failure_type", String.class), row.get("failure_reason", String.class),
                partition == null ? 0 : partition,
                offset == null ? 0 : offset, toInstant(row, "failed_at"), toInstant(row, "replayed_at"));
    }

    private String predicate(DeadLetterSearchQuery query) {
        List<String> conditions = new ArrayList<>();
        addIfPresent(conditions, query.getTopic(), "original_topic = :topic");
        if (query.getStatus() != null) {
            conditions.add(query.getStatus() == DeadLetterStatus.REPLAYED
                    ? "replayed_at IS NOT NULL" : "replayed_at IS NULL");
        }
        addIfPresent(conditions, query.getFailureType(), "failure_type = :failureType");
        addIfPresent(conditions, query.getFailedFrom(), "failed_at >= :failedFrom");
        addIfPresent(conditions, query.getFailedTo(), "failed_at <= :failedTo");
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    private DatabaseClient.GenericExecuteSpec bindFilters(DatabaseClient.GenericExecuteSpec spec,
                                                           DeadLetterSearchQuery query) {
        if (query.getTopic() != null) {
            spec = spec.bind("topic", query.getTopic());
        }
        if (query.getFailureType() != null) {
            spec = spec.bind("failureType", query.getFailureType());
        }
        if (query.getFailedFrom() != null) {
            spec = spec.bind("failedFrom", toDatabaseTime(query.getFailedFrom()));
        }
        if (query.getFailedTo() != null) {
            spec = spec.bind("failedTo", toDatabaseTime(query.getFailedTo()));
        }
        return spec;
    }

    private void addIfPresent(List<String> conditions, Object value, String condition) {
        if (value != null) {
            conditions.add(condition);
        }
    }

    private long number(Long value) {
        return value == null ? 0 : value;
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
