package org.zmy.observabilityplatform.incident.application.query;

import lombok.Getter;
import org.zmy.observabilityplatform.incident.domain.model.IncidentStatus;
import org.zmy.observabilityplatform.incident.domain.model.IncidentType;

import java.time.Instant;

@Getter
public final class IncidentSearchQuery {
    private static final int MAX_PAGE_SIZE = 100;

    private final IncidentStatus status;
    private final IncidentType type;
    private final String service;
    private final String environment;
    private final String assignee;
    private final Instant startedFrom;
    private final Instant startedTo;
    private final int page;
    private final int size;

    public IncidentSearchQuery(IncidentStatus status, IncidentType type, String service, String environment,
                               String assignee, Instant startedFrom, Instant startedTo, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (startedFrom != null && startedTo != null && startedFrom.isAfter(startedTo)) {
            throw new IllegalArgumentException("startedFrom must not be after startedTo");
        }
        this.status = status;
        this.type = type;
        this.service = blankToNull(service);
        this.environment = blankToNull(environment);
        this.assignee = blankToNull(assignee);
        this.startedFrom = startedFrom;
        this.startedTo = startedTo;
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
