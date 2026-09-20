package org.zmy.observabilityplatform.incident.application.service;

import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.audit.application.service.AuditOperation;
import org.zmy.observabilityplatform.audit.application.service.AuditResult;
import org.zmy.observabilityplatform.audit.application.service.AuditState;
import org.zmy.observabilityplatform.audit.application.service.AuditTrailService;
import org.zmy.observabilityplatform.audit.domain.model.AuditAction;
import org.zmy.observabilityplatform.audit.domain.model.AuditTargetType;
import org.zmy.observabilityplatform.incident.application.dto.IncidentChange;
import org.zmy.observabilityplatform.incident.application.dto.IncidentView;
import org.zmy.observabilityplatform.incident.domain.model.Incident;
import org.zmy.observabilityplatform.incident.domain.model.IncidentStatus;
import reactor.core.publisher.Mono;

import java.time.Clock;

@Service
public class IncidentManagementService {
    private final IncidentLifecycleService lifecycleService;
    private final Clock clock;
    private final AuditTrailService auditTrailService;

    public IncidentManagementService(IncidentLifecycleService lifecycleService, Clock clock,
                                     AuditTrailService auditTrailService) {
        this.lifecycleService = lifecycleService;
        this.clock = clock;
        this.auditTrailService = auditTrailService;
    }

    public Mono<IncidentView> assign(String incidentId, String assignee) {
        AuditOperation operation = new AuditOperation(AuditAction.INCIDENT_ASSIGN,
                AuditTargetType.INCIDENT, incidentId);
        return auditTrailService.audit(operation,
                        () -> lifecycleService.updateWithChange(incidentId,
                                existing -> existing.assignTo(assignee, clock.instant())),
                        this::auditResult)
                .map(IncidentChange::getAfter)
                .map(IncidentView::from);
    }

    public Mono<IncidentView> transition(String incidentId, IncidentStatus status, String resolution) {
        AuditOperation operation = new AuditOperation(AuditAction.INCIDENT_TRANSITION,
                AuditTargetType.INCIDENT, incidentId);
        return auditTrailService.audit(operation,
                        () -> lifecycleService.updateWithChange(incidentId,
                                existing -> existing.transitionTo(status, resolution, clock.instant())),
                        this::auditResult)
                .map(IncidentChange::getAfter)
                .map(IncidentView::from);
    }

    private AuditResult auditResult(IncidentChange change) {
        return AuditResult.changed(change.getAfter().getId(), snapshot(change.getBefore()),
                snapshot(change.getAfter()));
    }

    private java.util.Map<String, Object> snapshot(Incident incident) {
        return AuditState.of("status", incident.getStatus().name(),
                "assignee", incident.getAssignee(),
                "resolution", incident.getResolution(),
                "version", incident.getVersion(),
                "updatedAt", incident.getUpdatedAt().toString());
    }
}
