package org.zmy.observabilityplatform.logging.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Getter
@ToString
@EqualsAndHashCode
@Builder
@Jacksonized
public final class RawLogBatch {
    private final String batchId;
    private final String service;
    private final String environment;
    private final Instant receivedAt;
    private final List<RawLogRecord> logs;

    private RawLogBatch(String batchId, String service, String environment, Instant receivedAt,
                        List<RawLogRecord> logs) {
        this.batchId = requireText(batchId, "batchId");
        this.service = requireText(service, "service");
        this.environment = requireText(environment, "environment");
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        this.logs = List.copyOf(Objects.requireNonNull(logs, "logs must not be null"));
        if (this.logs.isEmpty()) {
            throw new IllegalArgumentException("The log batch must not be empty");
        }
    }

    public static RawLogBatch receive(String batchId, String service, String environment, Instant receivedAt,
                                      List<RawLogRecord> logs) {
        return new RawLogBatch(batchId, service, environment, receivedAt, logs);
    }

    public int size() {
        return logs.size();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
