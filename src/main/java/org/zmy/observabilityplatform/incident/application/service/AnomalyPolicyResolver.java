package org.zmy.observabilityplatform.incident.application.service;

import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicy;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicyScope;
import org.zmy.observabilityplatform.incident.domain.repository.AnomalyPolicyRepository;
import reactor.core.publisher.Mono;

@Service
public class AnomalyPolicyResolver {
    private final AnomalyPolicyRepository repository;

    public AnomalyPolicyResolver(AnomalyPolicyRepository repository) {
        this.repository = repository;
    }

    public Mono<AnomalyPolicy> resolve(String service, String environment, String operation) {
        return Mono.defer(() -> operationPolicy(service, environment, operation)
                .switchIfEmpty(repository.findByScope(
                        AnomalyPolicyScope.SERVICE, service, environment, null))
                .switchIfEmpty(repository.findByScope(
                        AnomalyPolicyScope.GLOBAL, null, null, null))
                .switchIfEmpty(Mono.error(new IllegalStateException("Global anomaly policy is missing"))));
    }

    private Mono<AnomalyPolicy> operationPolicy(String service, String environment, String operation) {
        if (operation == null || operation.isBlank()) {
            return Mono.empty();
        }
        return repository.findByScope(AnomalyPolicyScope.OPERATION, service, environment, operation);
    }
}
