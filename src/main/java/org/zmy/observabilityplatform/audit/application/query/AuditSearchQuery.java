package org.zmy.observabilityplatform.audit.application.query;

import lombok.Getter;
import org.zmy.observabilityplatform.audit.domain.model.AuditAction;
import org.zmy.observabilityplatform.audit.domain.model.AuditOutcome;
import org.zmy.observabilityplatform.audit.domain.model.AuditTargetType;

import java.time.Instant;

@Getter
public final class AuditSearchQuery {
    private static final int MAX_PAGE_SIZE = 100;

    private final String actor;
    private final AuditAction action;
    private final AuditTargetType targetType;
    private final String targetId;
    private final AuditOutcome outcome;
    private final Instant from;
    private final Instant to;
    private final int page;
    private final int size;

    public AuditSearchQuery(String actor, AuditAction action, AuditTargetType targetType, String targetId,
                            AuditOutcome outcome, Instant from, Instant to, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        this.actor = blankToNull(actor);
        this.action = action;
        this.targetType = targetType;
        this.targetId = blankToNull(targetId);
        this.outcome = outcome;
        this.from = from;
        this.to = to;
        this.page = page;
        this.size = size;
    }

    public long offset() {
        return (long) page * size;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
