package org.zmy.observabilityplatform.incident.infrastructure.repository.mysql;

import io.r2dbc.spi.Row;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.incident.domain.model.IncidentNotification;
import org.zmy.observabilityplatform.incident.domain.model.NotificationStatus;
import org.zmy.observabilityplatform.incident.domain.model.NotificationType;
import org.zmy.observabilityplatform.incident.domain.repository.IncidentNotificationRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class MySqlIncidentNotificationRepository implements IncidentNotificationRepository {
    private static final String COLUMNS = "id, notification_key, incident_id, notification_type, status, attempts, "
            + "created_at, updated_at, last_error, lease_owner, lease_until";
    private final DatabaseClient databaseClient;

    public MySqlIncidentNotificationRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Boolean> createIfAbsent(IncidentNotification notification) {
        return databaseClient.sql("""
                        INSERT INTO incident_notifications
                            (id, notification_key, incident_id, notification_type, status, attempts,
                             created_at, updated_at, last_error, lease_owner, lease_until)
                        VALUES (:id, :key, :incidentId, :type, :status, :attempts, :createdAt, :updatedAt, '', '', NULL)
                        ON DUPLICATE KEY UPDATE id = id
                        """)
                .bind("id", notification.getId())
                .bind("key", notification.getNotificationKey())
                .bind("incidentId", notification.getIncidentId())
                .bind("type", notification.getType().name())
                .bind("status", notification.getStatus().name())
                .bind("attempts", notification.getAttempts())
                .bind("createdAt", toDatabaseTime(notification.getCreatedAt()))
                .bind("updatedAt", toDatabaseTime(notification.getUpdatedAt()))
                .fetch().rowsUpdated().map(rows -> rows > 0);
    }

    @Override
    public Mono<IncidentNotification> claim(String notificationKey, String owner,
                                            Instant now, Instant leaseUntil) {
        return databaseClient.sql("""
                        UPDATE incident_notifications
                        SET status = 'SENDING', attempts = attempts + 1, updated_at = :now,
                            lease_owner = :owner, lease_until = :leaseUntil
                        WHERE notification_key = :key
                          AND (status IN ('PENDING', 'FAILED')
                               OR (status = 'SENDING' AND lease_until <= :now))
                        """)
                .bind("now", toDatabaseTime(now))
                .bind("owner", owner)
                .bind("leaseUntil", toDatabaseTime(leaseUntil))
                .bind("key", notificationKey)
                .fetch().rowsUpdated()
                .filter(rows -> rows == 1)
                .flatMap(rows -> findByKey(notificationKey));
    }

    @Override
    public Flux<IncidentNotification> claimRetryable(String owner, Instant now, Instant leaseUntil, int limit) {
        return databaseClient.sql("""
                        SELECT notification_key FROM incident_notifications
                        WHERE status IN ('PENDING', 'FAILED')
                           OR (status = 'SENDING' AND lease_until <= :now)
                        ORDER BY updated_at ASC LIMIT :limit
                        """)
                .bind("now", toDatabaseTime(now))
                .bind("limit", limit)
                .map((row, metadata) -> row.get("notification_key", String.class)).all()
                .concatMap(key -> claim(key, owner, now, leaseUntil));
    }

    @Override
    public Mono<Boolean> complete(IncidentNotification notification, String owner) {
        return databaseClient.sql("""
                        UPDATE incident_notifications
                        SET status = :status, attempts = :attempts, updated_at = :updatedAt,
                            last_error = :lastError, lease_owner = '', lease_until = NULL
                        WHERE notification_key = :key AND status = 'SENDING' AND lease_owner = :owner
                        """)
                .bind("status", notification.getStatus().name())
                .bind("attempts", notification.getAttempts())
                .bind("updatedAt", toDatabaseTime(notification.getUpdatedAt()))
                .bind("lastError", notification.getLastError() == null ? "" : notification.getLastError())
                .bind("key", notification.getNotificationKey())
                .bind("owner", owner)
                .fetch().rowsUpdated().map(rows -> rows == 1);
    }

    @Override
    public Flux<IncidentNotification> findByIncidentId(String incidentId, int limit) {
        return databaseClient.sql("SELECT " + COLUMNS
                        + " FROM incident_notifications WHERE incident_id = :incidentId "
                        + "ORDER BY created_at DESC LIMIT :limit")
                .bind("incidentId", incidentId).bind("limit", limit)
                .map((row, metadata) -> map(row)).all();
    }

    @Override
    public Mono<Long> deleteTerminalBefore(Instant cutoff, int limit) {
        if (limit < 1) {
            return Mono.error(new IllegalArgumentException("limit must be positive"));
        }
        return databaseClient.sql("""
                        DELETE FROM incident_notifications
                        WHERE status IN ('SENT', 'EXHAUSTED') AND updated_at < :cutoff
                        ORDER BY updated_at ASC
                        LIMIT :limit
                        """)
                .bind("cutoff", toDatabaseTime(cutoff))
                .bind("limit", limit)
                .fetch().rowsUpdated();
    }

    private IncidentNotification map(Row row) {
        return IncidentNotification.restore(row.get("id", String.class), row.get("notification_key", String.class),
                row.get("incident_id", String.class),
                NotificationType.valueOf(row.get("notification_type", String.class)),
                NotificationStatus.valueOf(row.get("status", String.class)),
                value(row.get("attempts", Integer.class)), toInstant(row, "created_at"),
                toInstant(row, "updated_at"), row.get("last_error", String.class),
                row.get("lease_owner", String.class), toInstant(row, "lease_until"));
    }

    private Mono<IncidentNotification> findByKey(String notificationKey) {
        return databaseClient.sql("SELECT " + COLUMNS
                        + " FROM incident_notifications WHERE notification_key = :key")
                .bind("key", notificationKey)
                .map((row, metadata) -> map(row)).one();
    }

    private int value(Integer value) {
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
