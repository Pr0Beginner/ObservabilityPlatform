package org.zmy.observabilityplatform.shared.messaging.application.query;

import lombok.Getter;
import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterStatus;

import java.time.Instant;

@Getter
public final class DeadLetterSearchQuery {
    private static final int MAX_PAGE_SIZE = 100;

    private final String topic;
    private final DeadLetterStatus status;
    private final String failureType;
    private final Instant failedFrom;
    private final Instant failedTo;
    private final int page;
    private final int size;

    public DeadLetterSearchQuery(String topic, DeadLetterStatus status, String failureType,
                                 Instant failedFrom, Instant failedTo, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (failedFrom != null && failedTo != null && failedFrom.isAfter(failedTo)) {
            throw new IllegalArgumentException("failedFrom must not be after failedTo");
        }
        this.topic = blankToNull(topic);
        this.status = status;
        this.failureType = blankToNull(failureType);
        this.failedFrom = failedFrom;
        this.failedTo = failedTo;
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
