package org.zmy.observabilityplatform.shared.messaging.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;

@Getter
@ToString
@EqualsAndHashCode
public final class DeadLetterReplayAttempt {
    private final String id;
    private final String deadLetterId;
    private final ReplayAttemptStatus status;
    private final Instant startedAt;
    private final Instant completedAt;
    private final String failureReason;

    private DeadLetterReplayAttempt(String id, String deadLetterId, ReplayAttemptStatus status,
                                    Instant startedAt, Instant completedAt, String failureReason) {
        this.id = requireText(id, "id");
        this.deadLetterId = requireText(deadLetterId, "deadLetterId");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        if (completedAt != null && completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("completedAt must not be before startedAt");
        }
        if (status == ReplayAttemptStatus.RUNNING && (completedAt != null || hasText(failureReason))) {
            throw new IllegalArgumentException("A running replay attempt cannot have a result");
        }
        if (status == ReplayAttemptStatus.SUCCEEDED && (completedAt == null || hasText(failureReason))) {
            throw new IllegalArgumentException("A successful replay attempt requires only a completion time");
        }
        if (status == ReplayAttemptStatus.FAILED && (completedAt == null || !hasText(failureReason))) {
            throw new IllegalArgumentException("A failed replay attempt requires a failure reason");
        }
        this.completedAt = completedAt;
        this.failureReason = hasText(failureReason) ? failureReason : null;
    }

    public static DeadLetterReplayAttempt start(String id, String deadLetterId, Instant startedAt) {
        return new DeadLetterReplayAttempt(id, deadLetterId, ReplayAttemptStatus.RUNNING,
                startedAt, null, null);
    }

    public static DeadLetterReplayAttempt restore(String id, String deadLetterId, ReplayAttemptStatus status,
                                                  Instant startedAt, Instant completedAt, String failureReason) {
        return new DeadLetterReplayAttempt(id, deadLetterId, status, startedAt, completedAt, failureReason);
    }

    public DeadLetterReplayAttempt succeed(Instant now) {
        requireRunning();
        return new DeadLetterReplayAttempt(id, deadLetterId, ReplayAttemptStatus.SUCCEEDED,
                startedAt, now, null);
    }

    public DeadLetterReplayAttempt fail(String reason, Instant now) {
        requireRunning();
        return new DeadLetterReplayAttempt(id, deadLetterId, ReplayAttemptStatus.FAILED,
                startedAt, now, requireText(reason, "failureReason"));
    }

    private void requireRunning() {
        if (status != ReplayAttemptStatus.RUNNING) {
            throw new IllegalStateException("Replay attempt is already terminal: " + status);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String requireText(String value, String field) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
