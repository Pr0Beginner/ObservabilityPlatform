package org.zmy.observabilityplatform.diagnosis.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Getter
@ToString
@EqualsAndHashCode
public final class DiagnosisReport {
    private final String id;
    private final String taskId;
    private final int version;
    private final String rootCause;
    private final double confidence;
    private final List<String> evidence;
    private final List<String> recommendations;
    private final List<String> toolCalls;
    private final Instant generatedAt;

    private DiagnosisReport(String id, String taskId, int version, String rootCause, double confidence,
                            List<String> evidence, List<String> recommendations, List<String> toolCalls,
                            Instant generatedAt) {
        this.id = requireText(id, "id");
        this.taskId = requireText(taskId, "taskId");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        if (confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        this.version = version;
        this.rootCause = requireText(rootCause, "rootCause");
        this.confidence = confidence;
        this.evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence must not be null"));
        this.recommendations = List.copyOf(
                Objects.requireNonNull(recommendations, "recommendations must not be null"));
        this.toolCalls = List.copyOf(Objects.requireNonNull(toolCalls, "toolCalls must not be null"));
        this.generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
    }

    public static DiagnosisReport generate(String id, String taskId, int version, String rootCause,
                                           double confidence, List<String> evidence,
                                           List<String> recommendations, List<String> toolCalls,
                                           Instant generatedAt) {
        return new DiagnosisReport(id, taskId, version, rootCause, confidence, evidence,
                recommendations, toolCalls, generatedAt);
    }

    public static DiagnosisReport restore(String id, String taskId, int version, String rootCause,
                                          double confidence, List<String> evidence,
                                          List<String> recommendations, List<String> toolCalls,
                                          Instant generatedAt) {
        return new DiagnosisReport(id, taskId, version, rootCause, confidence, evidence,
                recommendations, toolCalls, generatedAt);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
