package org.zmy.observabilityplatform.shared.messaging.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.zmy.observabilityplatform.shared.exception.BusinessConflictException;

import java.time.Instant;
import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Getter
@ToString
@EqualsAndHashCode
public final class DeadLetterMessage {
    private final String id;
    private final String originalTopic;
    private final String messageKey;
    private final String payload;
    private final String failureType;
    private final String failureReason;
    private final int sourcePartition;
    private final long sourceOffset;
    private final Instant failedAt;
    private final Instant replayedAt;

    private DeadLetterMessage(String id, String originalTopic, String messageKey, String payload,
                              String failureType, String failureReason, int sourcePartition, long sourceOffset,
                              Instant failedAt, Instant replayedAt) {
        this.id = requireText(id, "id");
        this.originalTopic = requireText(originalTopic, "originalTopic");
        this.messageKey = messageKey;
        this.payload = payload == null ? "" : payload;
        this.failureType = requireText(failureType, "failureType");
        this.failureReason = requireText(failureReason, "failureReason");
        this.sourcePartition = sourcePartition;
        this.sourceOffset = sourceOffset;
        this.failedAt = Objects.requireNonNull(failedAt, "failedAt must not be null");
        this.replayedAt = replayedAt;
    }

    public static DeadLetterMessage captured(String originalTopic, String messageKey, String payload,
                                             String failureReason, int sourcePartition, long sourceOffset,
                                             Instant failedAt) {
        return captured(originalTopic, messageKey, payload, "UNKNOWN", failureReason,
                sourcePartition, sourceOffset, failedAt);
    }

    public static DeadLetterMessage captured(String originalTopic, String messageKey, String payload,
                                             String failureType, String failureReason,
                                             int sourcePartition, long sourceOffset, Instant failedAt) {
        String id = stableId(originalTopic, sourcePartition, sourceOffset);
        return new DeadLetterMessage(id, originalTopic, messageKey, payload, failureType, failureReason,
                sourcePartition, sourceOffset, failedAt, null);
    }

    public static DeadLetterMessage restore(String id, String originalTopic, String messageKey, String payload,
                                            String failureReason, int sourcePartition, long sourceOffset,
                                            Instant failedAt, Instant replayedAt) {
        return restore(id, originalTopic, messageKey, payload, "UNKNOWN", failureReason,
                sourcePartition, sourceOffset, failedAt, replayedAt);
    }

    public static DeadLetterMessage restore(String id, String originalTopic, String messageKey, String payload,
                                            String failureType, String failureReason,
                                            int sourcePartition, long sourceOffset,
                                            Instant failedAt, Instant replayedAt) {
        return new DeadLetterMessage(id, originalTopic, messageKey, payload, failureType, failureReason,
                sourcePartition, sourceOffset, failedAt, replayedAt);
    }

    public DeadLetterMessage replayed(Instant now) {
        if (replayedAt != null) {
            throw new BusinessConflictException("Dead-letter message has already been replayed: " + id);
        }
        return new DeadLetterMessage(id, originalTopic, messageKey, payload, failureType, failureReason,
                sourcePartition, sourceOffset, failedAt, now);
    }

    public DeadLetterStatus getStatus() {
        return replayedAt == null ? DeadLetterStatus.UNRESOLVED : DeadLetterStatus.REPLAYED;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String stableId(String topic, int partition, long offset) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    (topic + ":" + partition + ":" + offset).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
