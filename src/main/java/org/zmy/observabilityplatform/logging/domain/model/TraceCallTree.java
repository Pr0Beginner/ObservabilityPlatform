package org.zmy.observabilityplatform.logging.domain.model;

import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

@Getter
public final class TraceCallTree {
    private final String traceId;
    private final List<TraceSpan> spans;
    private final List<LogEntry> unassignedLogs;
    private final boolean truncated;

    public TraceCallTree(String traceId, List<TraceSpan> spans, List<LogEntry> unassignedLogs, boolean truncated) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        this.traceId = traceId;
        this.spans = spans.stream().sorted(Comparator.comparing(TraceSpan::getStartedAt)).toList();
        this.unassignedLogs = unassignedLogs.stream()
                .sorted(Comparator.comparing(LogEntry::getTimestamp)).toList();
        this.truncated = truncated;
        if (this.spans.isEmpty() && this.unassignedLogs.isEmpty()) {
            throw new IllegalArgumentException("A trace must contain at least one log");
        }
        boolean foreignLog = java.util.stream.Stream.concat(
                        this.spans.stream().flatMap(span -> span.getLogs().stream()),
                        this.unassignedLogs.stream())
                .anyMatch(log -> !traceId.equals(log.getTraceId()));
        if (foreignLog) {
            throw new IllegalArgumentException("All logs must belong to trace " + traceId);
        }
    }

    public List<TraceSpan> roots() {
        return spans.stream().filter(span -> span.getParentSpanId() == null).toList();
    }

    public List<TraceSpan> childrenOf(String spanId) {
        return spans.stream().filter(span -> spanId.equals(span.getParentSpanId())).toList();
    }

    public List<String> services() {
        LinkedHashSet<String> services = new LinkedHashSet<>();
        spans.forEach(span -> services.add(span.getService()));
        unassignedLogs.forEach(log -> services.add(log.getService()));
        return List.copyOf(services);
    }

    public Instant startedAt() {
        return allTimestamps().stream().min(Instant::compareTo).orElseThrow();
    }

    public Instant endedAt() {
        Instant spanEnd = spans.stream().map(TraceSpan::getEndedAt).max(Instant::compareTo).orElse(null);
        Instant logEnd = unassignedLogs.stream().map(LogEntry::getTimestamp).max(Instant::compareTo).orElse(null);
        if (spanEnd == null) {
            return logEnd;
        }
        return logEnd != null && logEnd.isAfter(spanEnd) ? logEnd : spanEnd;
    }

    public long durationMs() {
        return Math.max(0, Duration.between(startedAt(), endedAt()).toMillis());
    }

    public boolean isSuccessful() {
        boolean spansSucceeded = spans.stream().allMatch(TraceSpan::isSuccess);
        boolean looseLogsSucceeded = unassignedLogs.stream().noneMatch(LogEntry::isError);
        return spansSucceeded && looseLogsSucceeded;
    }

    private List<Instant> allTimestamps() {
        return java.util.stream.Stream.concat(
                        spans.stream().map(TraceSpan::getStartedAt),
                        unassignedLogs.stream().map(LogEntry::getTimestamp))
                .toList();
    }
}
