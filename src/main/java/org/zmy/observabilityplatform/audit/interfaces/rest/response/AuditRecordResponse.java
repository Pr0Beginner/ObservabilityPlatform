package org.zmy.observabilityplatform.audit.interfaces.rest.response;

import lombok.Getter;
import org.zmy.observabilityplatform.audit.domain.model.AuditRecord;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
public final class AuditRecordResponse {
    private final String id;
    private final String actor;
    private final List<String> roles;
    private final String action;
    private final String targetType;
    private final String targetId;
    private final String outcome;
    private final String traceId;
    private final Instant occurredAt;
    private final Map<String, Object> beforeState;
    private final Map<String, Object> afterState;
    private final String failureReason;

    private AuditRecordResponse(AuditRecord record) {
        this.id = record.getId();
        this.actor = record.getActor();
        this.roles = record.getRoles();
        this.action = record.getAction().name();
        this.targetType = record.getTargetType().name();
        this.targetId = record.getTargetId();
        this.outcome = record.getOutcome().name();
        this.traceId = record.getTraceId();
        this.occurredAt = record.getOccurredAt();
        this.beforeState = record.getBeforeState();
        this.afterState = record.getAfterState();
        this.failureReason = record.getFailureReason();
    }

    public static AuditRecordResponse from(AuditRecord record) {
        return new AuditRecordResponse(record);
    }
}
