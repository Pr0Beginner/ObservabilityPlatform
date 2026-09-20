package org.zmy.observabilityplatform.audit.application.service;

import lombok.Getter;

import java.util.Map;

@Getter
public final class AuditResult {
    private final String targetId;
    private final Map<String, Object> beforeState;
    private final Map<String, Object> afterState;

    public AuditResult(String targetId, Map<String, Object> beforeState, Map<String, Object> afterState) {
        this.targetId = targetId;
        this.beforeState = beforeState == null ? Map.of() : Map.copyOf(beforeState);
        this.afterState = afterState == null ? Map.of() : Map.copyOf(afterState);
    }

    public static AuditResult created(String targetId, Map<String, Object> afterState) {
        return new AuditResult(targetId, Map.of(), afterState);
    }

    public static AuditResult changed(String targetId, Map<String, Object> beforeState,
                                      Map<String, Object> afterState) {
        return new AuditResult(targetId, beforeState, afterState);
    }
}
