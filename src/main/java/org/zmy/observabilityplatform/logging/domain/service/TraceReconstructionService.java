package org.zmy.observabilityplatform.logging.domain.service;

import org.zmy.observabilityplatform.logging.domain.model.LogEntry;
import org.zmy.observabilityplatform.logging.domain.model.TraceCallTree;
import org.zmy.observabilityplatform.logging.domain.model.TraceSpan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TraceReconstructionService {
    public TraceCallTree reconstruct(String traceId, List<LogEntry> logs, boolean truncated) {
        Map<String, List<LogEntry>> logsBySpan = new LinkedHashMap<>();
        List<LogEntry> unassigned = new ArrayList<>();
        logs.forEach(log -> {
            if (log.getSpanId() == null || log.getSpanId().isBlank()) {
                unassigned.add(log);
            } else {
                logsBySpan.computeIfAbsent(log.getSpanId(), ignored -> new ArrayList<>()).add(log);
            }
        });

        Map<String, String> parents = new LinkedHashMap<>();
        logsBySpan.forEach((spanId, spanLogs) -> parents.put(spanId, firstParent(spanLogs)));
        parents.replaceAll((spanId, parentId) -> validParent(spanId, parentId, parents));

        List<TraceSpan> spans = logsBySpan.entrySet().stream()
                .map(entry -> TraceSpan.reconstruct(entry.getKey(), parents.get(entry.getKey()), entry.getValue()))
                .toList();
        return new TraceCallTree(traceId, spans, unassigned, truncated);
    }

    private String firstParent(List<LogEntry> logs) {
        return logs.stream().map(LogEntry::getParentSpanId)
                .filter(value -> value != null && !value.isBlank())
                .findFirst().orElse(null);
    }

    private String validParent(String spanId, String parentId, Map<String, String> parents) {
        if (parentId == null || spanId.equals(parentId) || !parents.containsKey(parentId)) {
            return null;
        }
        Set<String> visited = new LinkedHashSet<>();
        visited.add(spanId);
        String current = parentId;
        while (current != null) {
            if (!visited.add(current)) {
                return null;
            }
            current = parents.get(current);
        }
        return parentId;
    }
}
