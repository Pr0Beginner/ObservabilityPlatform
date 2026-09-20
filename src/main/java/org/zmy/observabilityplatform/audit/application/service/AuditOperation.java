package org.zmy.observabilityplatform.audit.application.service;

import lombok.Getter;
import org.zmy.observabilityplatform.audit.domain.model.AuditAction;
import org.zmy.observabilityplatform.audit.domain.model.AuditTargetType;

import java.util.Objects;

@Getter
public final class AuditOperation {
    private final AuditAction action;
    private final AuditTargetType targetType;
    private final String targetId;

    public AuditOperation(AuditAction action, AuditTargetType targetType, String targetId) {
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.targetType = Objects.requireNonNull(targetType, "targetType must not be null");
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId must not be blank");
        }
        this.targetId = targetId;
    }
}
