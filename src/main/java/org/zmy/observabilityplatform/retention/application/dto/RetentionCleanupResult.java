package org.zmy.observabilityplatform.retention.application.dto;

import lombok.Value;

import java.time.Duration;
import java.time.Instant;

@Value
public class RetentionCleanupResult {
    String target;
    long deletedRecords;
    Instant startedAt;
    Duration duration;
    String failure;

    public static RetentionCleanupResult succeeded(String target, long deletedRecords,
                                                   Instant startedAt, Duration duration) {
        return new RetentionCleanupResult(target, deletedRecords, startedAt, duration, null);
    }

    public static RetentionCleanupResult failed(String target, long deletedRecords,
                                                Instant startedAt, Duration duration, String failure) {
        return new RetentionCleanupResult(target, deletedRecords, startedAt, duration, failure);
    }

    public boolean isSuccessful() {
        return failure == null;
    }
}
