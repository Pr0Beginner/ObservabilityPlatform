package org.zmy.observabilityplatform.incident.application.service;

import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.audit.application.service.AuditOperation;
import org.zmy.observabilityplatform.audit.application.service.AuditResult;
import org.zmy.observabilityplatform.audit.application.service.AuditState;
import org.zmy.observabilityplatform.audit.application.service.AuditTrailService;
import org.zmy.observabilityplatform.audit.domain.model.AuditAction;
import org.zmy.observabilityplatform.audit.domain.model.AuditTargetType;
import org.zmy.observabilityplatform.incident.application.command.CreateAnomalyPolicyCommand;
import org.zmy.observabilityplatform.incident.application.command.UpdateAnomalyPolicyCommand;
import org.zmy.observabilityplatform.incident.application.dto.AnomalyPolicyView;
import org.zmy.observabilityplatform.incident.domain.exception.AnomalyPolicyConflictException;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicy;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyDetectionSettings;
import org.zmy.observabilityplatform.incident.domain.repository.AnomalyPolicyRepository;
import org.zmy.observabilityplatform.shared.exception.NotFoundException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.UUID;

@Service
public class AnomalyPolicyManagementService {
    private final AnomalyPolicyRepository repository;
    private final Clock clock;
    private final AuditTrailService auditTrailService;

    public AnomalyPolicyManagementService(AnomalyPolicyRepository repository, Clock clock,
                                          AuditTrailService auditTrailService) {
        this.repository = repository;
        this.clock = clock;
        this.auditTrailService = auditTrailService;
    }

    public Mono<AnomalyPolicyView> create(CreateAnomalyPolicyCommand command) {
        AuditOperation operation = new AuditOperation(AuditAction.ANOMALY_POLICY_CREATE,
                AuditTargetType.ANOMALY_POLICY, "new");
        return auditTrailService.audit(operation,
                        () -> Mono.defer(() -> repository.create(AnomalyPolicy.create(
                                UUID.randomUUID().toString(), command.getName(), command.getScope(),
                                command.getService(), command.getEnvironment(), command.getOperation(),
                                command.isEnabled(), command.settings(), clock.instant()))),
                        policy -> AuditResult.created(policy.getId(), snapshot(policy)))
                .map(AnomalyPolicyView::from);
    }

    public Mono<AnomalyPolicyView> update(String policyId, UpdateAnomalyPolicyCommand command) {
        AuditOperation operation = new AuditOperation(AuditAction.ANOMALY_POLICY_UPDATE,
                AuditTargetType.ANOMALY_POLICY, policyId);
        return auditTrailService.audit(operation, () -> find(policyId).flatMap(existing -> {
                    if (existing.getVersion() != command.getVersion()) {
                        return Mono.error(AnomalyPolicyConflictException.staleVersion(
                                policyId, command.getVersion()));
                    }
                    AnomalyPolicy revised = existing.revise(command.getName(), command.getScope(),
                            command.getService(), command.getEnvironment(), command.getOperation(),
                            command.isEnabled(), command.settings(), clock.instant());
                    return repository.update(revised).map(saved -> new PolicyChange(existing, saved));
                }), change -> AuditResult.changed(policyId, snapshot(change.before), snapshot(change.after)))
                .map(change -> AnomalyPolicyView.from(change.after));
    }

    public Mono<AnomalyPolicyView> findById(String policyId) {
        return find(policyId).map(AnomalyPolicyView::from);
    }

    public Flux<AnomalyPolicyView> findAll() {
        return repository.findAll().map(AnomalyPolicyView::from);
    }

    private Mono<AnomalyPolicy> find(String policyId) {
        return repository.findById(policyId)
                .switchIfEmpty(Mono.error(new NotFoundException("Anomaly policy not found: " + policyId)));
    }

    private java.util.Map<String, Object> snapshot(AnomalyPolicy policy) {
        return AuditState.of("name", policy.getName(),
                "scope", policy.getScope().name(),
                "service", policy.getService(),
                "environment", policy.getEnvironment(),
                "operation", policy.getOperation(),
                "enabled", policy.isEnabled(),
                "version", policy.getVersion(),
                "settings", settingsSnapshot(policy.getSettings()));
    }

    private java.util.Map<String, Object> settingsSnapshot(AnomalyDetectionSettings settings) {
        return AuditState.of("errorThreshold", settings.getErrorThreshold(),
                "minimumRequests", settings.getMinimumRequests(),
                "minimumErrorCodeCount", settings.getMinimumErrorCodeCount(),
                "requestSpikeRatio", settings.getRequestSpikeRatio(),
                "requestDropRatio", settings.getRequestDropRatio(),
                "failureRateThreshold", settings.getFailureRateThreshold(),
                "errorCodeRateThreshold", settings.getErrorCodeRateThreshold(),
                "baselineMultiplier", settings.getBaselineMultiplier(),
                "comparisonPeriodMinutes", settings.getComparisonPeriodMinutes(),
                "recoveryWindows", settings.getRecoveryWindows());
    }

    private static final class PolicyChange {
        private final AnomalyPolicy before;
        private final AnomalyPolicy after;

        private PolicyChange(AnomalyPolicy before, AnomalyPolicy after) {
            this.before = before;
            this.after = after;
        }
    }
}
