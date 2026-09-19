package org.zmy.observabilityplatform.incident.application.service;

import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.incident.application.command.CreateAnomalyPolicyCommand;
import org.zmy.observabilityplatform.incident.application.command.UpdateAnomalyPolicyCommand;
import org.zmy.observabilityplatform.incident.application.dto.AnomalyPolicyView;
import org.zmy.observabilityplatform.incident.domain.exception.AnomalyPolicyConflictException;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicy;
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

    public AnomalyPolicyManagementService(AnomalyPolicyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Mono<AnomalyPolicyView> create(CreateAnomalyPolicyCommand command) {
        return Mono.defer(() -> repository.create(AnomalyPolicy.create(
                        UUID.randomUUID().toString(), command.getName(), command.getScope(),
                        command.getService(), command.getEnvironment(), command.getOperation(),
                        command.isEnabled(), command.settings(), clock.instant())))
                .map(AnomalyPolicyView::from);
    }

    public Mono<AnomalyPolicyView> update(String policyId, UpdateAnomalyPolicyCommand command) {
        return find(policyId).flatMap(existing -> {
            if (existing.getVersion() != command.getVersion()) {
                return Mono.error(AnomalyPolicyConflictException.staleVersion(
                        policyId, command.getVersion()));
            }
            AnomalyPolicy revised = existing.revise(command.getName(), command.getScope(),
                    command.getService(), command.getEnvironment(), command.getOperation(),
                    command.isEnabled(), command.settings(), clock.instant());
            return repository.update(revised);
        }).map(AnomalyPolicyView::from);
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
}
