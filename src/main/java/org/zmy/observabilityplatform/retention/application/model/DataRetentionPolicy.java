package org.zmy.observabilityplatform.retention.application.model;

import lombok.Value;

import java.time.Duration;
import java.util.Objects;

@Value
public class DataRetentionPolicy {
    Duration metricWindowRetention;
    Duration metricBaselineSafety;
    Duration notificationRetention;
    Duration replayedDeadLetterRetention;
    Duration unresolvedDeadLetterRetention;
    Duration auditRetention;
    int batchSize;
    int maxBatchesPerRun;

    public DataRetentionPolicy(Duration metricWindowRetention, Duration metricBaselineSafety,
                               Duration notificationRetention, Duration replayedDeadLetterRetention,
                               Duration unresolvedDeadLetterRetention, Duration auditRetention,
                               int batchSize, int maxBatchesPerRun) {
        this.metricWindowRetention = requirePositive(metricWindowRetention, "metricWindowRetention");
        this.metricBaselineSafety = requireNonNegative(metricBaselineSafety, "metricBaselineSafety");
        this.notificationRetention = requirePositive(notificationRetention, "notificationRetention");
        this.replayedDeadLetterRetention = requirePositive(
                replayedDeadLetterRetention, "replayedDeadLetterRetention");
        this.unresolvedDeadLetterRetention = requirePositive(
                unresolvedDeadLetterRetention, "unresolvedDeadLetterRetention");
        this.auditRetention = requirePositive(auditRetention, "auditRetention");
        if (batchSize < 1 || maxBatchesPerRun < 1) {
            throw new IllegalArgumentException("batchSize and maxBatchesPerRun must be positive");
        }
        this.batchSize = batchSize;
        this.maxBatchesPerRun = maxBatchesPerRun;
    }

    private static Duration requirePositive(Duration value, String field) {
        Duration duration = Objects.requireNonNull(value, field + " must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return duration;
    }

    private static Duration requireNonNegative(Duration value, String field) {
        Duration duration = Objects.requireNonNull(value, field + " must not be null");
        if (duration.isNegative()) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return duration;
    }
}
