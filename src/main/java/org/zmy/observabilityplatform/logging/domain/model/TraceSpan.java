package org.zmy.observabilityplatform.logging.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Getter
@ToString
@EqualsAndHashCode
public final class TraceSpan {
    private final String spanId;
    private final String parentSpanId;
    private final String service;
    private final String operation;
    private final String spanKind;
    private final Instant startedAt;
    private final Instant endedAt;
    private final long durationMs;
    private final boolean success;
    private final Integer statusCode;
    private final String errorCode;
    private final List<LogEntry> logs;

    private TraceSpan(String spanId, String parentSpanId, List<LogEntry> source) {
        this.spanId = requireText(spanId, "spanId");
        this.parentSpanId = blankToNull(parentSpanId);
        this.logs = source.stream().sorted(Comparator.comparing(LogEntry::getTimestamp)).toList();
        if (logs.isEmpty()) {
            throw new IllegalArgumentException("Trace span logs must not be empty");
        }
        this.service = logs.get(0).getService();
        this.operation = firstText(logs.stream().map(LogEntry::getOperation).toList());
        this.spanKind = firstText(logs.stream().map(LogEntry::getSpanKind).toList());
        this.startedAt = logs.get(0).getTimestamp();
        Instant latestLogAt = logs.get(logs.size() - 1).getTimestamp();
        long reportedDuration = logs.stream()
                .map(LogEntry::getDurationMs)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(0L);
        this.endedAt = latestLogAt.isAfter(startedAt.plusMillis(reportedDuration))
                ? latestLogAt : startedAt.plusMillis(reportedDuration);
        this.durationMs = Math.max(0, Duration.between(startedAt, endedAt).toMillis());
        this.statusCode = logs.stream().map(LogEntry::getStatusCode).filter(Objects::nonNull)
                .reduce((first, second) -> second).orElse(null);
        this.errorCode = firstText(logs.stream().map(LogEntry::getErrorCode).toList());
        this.success = logs.stream().noneMatch(log -> Boolean.FALSE.equals(log.getSuccess())
                || (log.getStatusCode() != null && log.getStatusCode() >= 400)
                || log.isError());
    }

    public static TraceSpan reconstruct(String spanId, String parentSpanId, List<LogEntry> logs) {
        return new TraceSpan(spanId, parentSpanId, List.copyOf(logs));
    }

    private static String firstText(List<String> values) {
        return values.stream().filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
