package org.zmy.observabilityplatform.audit.domain.model;

import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Getter
public final class AuditRecord {
    private final String id;
    private final String actor;
    private final List<String> roles;
    private final AuditAction action;
    private final AuditTargetType targetType;
    private final String targetId;
    private final AuditOutcome outcome;
    private final String traceId;
    private final Instant occurredAt;
    private final Map<String, Object> beforeState;
    private final Map<String, Object> afterState;
    private final String failureReason;

    private AuditRecord(String id, String actor, List<String> roles, AuditAction action,
                        AuditTargetType targetType, String targetId, AuditOutcome outcome,
                        String traceId, Instant occurredAt, Map<String, Object> beforeState,
                        Map<String, Object> afterState, String failureReason) {
        this.id = requireText(id, "id");
        this.actor = requireText(actor, "actor");
        this.roles = roles == null ? List.of() : List.copyOf(roles);
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.targetType = Objects.requireNonNull(targetType, "targetType must not be null");
        this.targetId = requireText(targetId, "targetId");
        this.outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        this.traceId = requireText(traceId, "traceId");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        this.beforeState = beforeState == null ? Map.of() : Map.copyOf(beforeState);
        this.afterState = afterState == null ? Map.of() : Map.copyOf(afterState);
        if (outcome == AuditOutcome.SUCCEEDED && hasText(failureReason)) {
            throw new IllegalArgumentException("A successful audit record cannot have a failure reason");
        }
        if (outcome != AuditOutcome.SUCCEEDED && !hasText(failureReason)) {
            throw new IllegalArgumentException("A failed or denied audit record requires a failure reason");
        }
        this.failureReason = hasText(failureReason) ? failureReason : null;
    }

    public static AuditRecord create(String id, AuditActor actor, AuditAction action,
                                     AuditTargetType targetType, String targetId, AuditOutcome outcome,
                                     String traceId, Instant occurredAt, Map<String, Object> beforeState,
                                     Map<String, Object> afterState, String failureReason) {
        return new AuditRecord(id, actor.getName(), actor.getRoles(), action, targetType, targetId,
                outcome, traceId, occurredAt, beforeState, afterState, failureReason);
    }

    public static AuditRecord restore(String id, String actor, List<String> roles, AuditAction action,
                                      AuditTargetType targetType, String targetId, AuditOutcome outcome,
                                      String traceId, Instant occurredAt, Map<String, Object> beforeState,
                                      Map<String, Object> afterState, String failureReason) {
        return new AuditRecord(id, actor, roles, action, targetType, targetId, outcome,
                traceId, occurredAt, beforeState, afterState, failureReason);
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
