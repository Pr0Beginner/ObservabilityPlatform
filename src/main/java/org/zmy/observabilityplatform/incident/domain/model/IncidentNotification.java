package org.zmy.observabilityplatform.incident.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;

@Getter
@ToString
@EqualsAndHashCode
public final class IncidentNotification {
    private final String id;
    private final String notificationKey;
    private final String incidentId;
    private final NotificationType type;
    private final NotificationStatus status;
    private final int attempts;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String lastError;
    private final String leaseOwner;
    private final Instant leaseUntil;

    private IncidentNotification(String id, String notificationKey, String incidentId, NotificationType type,
                                 NotificationStatus status, int attempts, Instant createdAt, Instant updatedAt,
                                 String lastError, String leaseOwner, Instant leaseUntil) {
        this.id = requireText(id, "id");
        this.notificationKey = requireText(notificationKey, "notificationKey");
        this.incidentId = requireText(incidentId, "incidentId");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts must not be negative");
        }
        this.attempts = attempts;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        this.lastError = lastError == null || lastError.isBlank() ? null : lastError;
        this.leaseOwner = leaseOwner == null || leaseOwner.isBlank() ? null : leaseOwner;
        this.leaseUntil = leaseUntil;
        if (status == NotificationStatus.SENDING && (this.leaseOwner == null || leaseUntil == null)) {
            throw new IllegalArgumentException("A sending notification requires an active lease");
        }
        if (status == NotificationStatus.SENDING && !leaseUntil.isAfter(updatedAt)) {
            throw new IllegalArgumentException("A notification lease must expire after updatedAt");
        }
        if (status != NotificationStatus.SENDING && (this.leaseOwner != null || leaseUntil != null)) {
            throw new IllegalArgumentException("Only a sending notification can own a lease");
        }
    }

    public static IncidentNotification pending(String id, String notificationKey, String incidentId,
                                               NotificationType type, Instant now) {
        return new IncidentNotification(id, notificationKey, incidentId, type, NotificationStatus.PENDING,
                0, now, now, null, null, null);
    }

    public static IncidentNotification restore(String id, String notificationKey, String incidentId,
                                               NotificationType type, NotificationStatus status, int attempts,
                                               Instant createdAt, Instant updatedAt, String lastError,
                                               String leaseOwner, Instant leaseUntil) {
        return new IncidentNotification(id, notificationKey, incidentId, type, status, attempts,
                createdAt, updatedAt, lastError, leaseOwner, leaseUntil);
    }

    public boolean canBeClaimedAt(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return status == NotificationStatus.PENDING || status == NotificationStatus.FAILED
                || status == NotificationStatus.SENDING && !leaseUntil.isAfter(now);
    }

    public IncidentNotification claim(String owner, Instant until, Instant now) {
        if (!canBeClaimedAt(now)) {
            throw new IllegalStateException("Notification cannot be claimed in status " + status);
        }
        if (!until.isAfter(now)) {
            throw new IllegalArgumentException("leaseUntil must be after now");
        }
        return new IncidentNotification(id, notificationKey, incidentId, type, NotificationStatus.SENDING,
                attempts + 1, createdAt, now, lastError, requireText(owner, "owner"), until);
    }

    public IncidentNotification sent(Instant now) {
        requireSending();
        return new IncidentNotification(id, notificationKey, incidentId, type, NotificationStatus.SENT,
                attempts, createdAt, now, null, null, null);
    }

    public IncidentNotification deliveryFailed(String error, int maxAttempts, Instant now) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        requireSending();
        NotificationStatus nextStatus = attempts >= maxAttempts
                ? NotificationStatus.EXHAUSTED : NotificationStatus.FAILED;
        return new IncidentNotification(id, notificationKey, incidentId, type, nextStatus,
                attempts, createdAt, now, requireText(error, "error"), null, null);
    }

    private void requireSending() {
        if (status != NotificationStatus.SENDING) {
            throw new IllegalStateException("Only a claimed notification can complete delivery");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
