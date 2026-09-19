package org.zmy.observabilityplatform.incident.application.service;

import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.incident.application.dto.IncidentView;
import org.zmy.observabilityplatform.incident.domain.model.IncidentStatus;
import reactor.core.publisher.Mono;

import java.time.Clock;

@Service
public class IncidentManagementService {
    private final IncidentLifecycleService lifecycleService;
    private final Clock clock;

    public IncidentManagementService(IncidentLifecycleService lifecycleService, Clock clock) {
        this.lifecycleService = lifecycleService;
        this.clock = clock;
    }

    public Mono<IncidentView> assign(String incidentId, String assignee) {
        return lifecycleService.update(incidentId, existing -> existing.assignTo(assignee, clock.instant()))
                .map(IncidentView::from);
    }

    public Mono<IncidentView> transition(String incidentId, IncidentStatus status, String resolution) {
        return lifecycleService.update(incidentId,
                        existing -> existing.transitionTo(status, resolution, clock.instant()))
                .map(IncidentView::from);
    }
}
