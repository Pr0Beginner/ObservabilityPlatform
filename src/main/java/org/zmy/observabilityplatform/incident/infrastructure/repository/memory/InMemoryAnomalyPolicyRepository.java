package org.zmy.observabilityplatform.incident.infrastructure.repository.memory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.incident.domain.exception.AnomalyPolicyConflictException;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicy;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicyScope;
import org.zmy.observabilityplatform.incident.domain.repository.AnomalyPolicyRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "local", matchIfMissing = true)
public class InMemoryAnomalyPolicyRepository implements AnomalyPolicyRepository {
    private final Map<String, AnomalyPolicy> policies = new LinkedHashMap<>();

    public InMemoryAnomalyPolicyRepository() {
        AnomalyPolicy defaultPolicy = AnomalyPolicy.globalDefault(Instant.EPOCH);
        policies.put(defaultPolicy.getId(), defaultPolicy);
    }

    @Override
    public synchronized Mono<AnomalyPolicy> create(AnomalyPolicy policy) {
        if (policies.containsKey(policy.getId()) || hasScope(policy.scopeKey(), null)) {
            return Mono.error(AnomalyPolicyConflictException.duplicateScope());
        }
        policies.put(policy.getId(), policy);
        return Mono.just(policy);
    }

    @Override
    public synchronized Mono<AnomalyPolicy> update(AnomalyPolicy policy) {
        AnomalyPolicy current = policies.get(policy.getId());
        long expectedVersion = policy.getVersion() - 1;
        if (current == null || current.getVersion() != expectedVersion) {
            return Mono.error(AnomalyPolicyConflictException.staleVersion(policy.getId(), expectedVersion));
        }
        if (hasScope(policy.scopeKey(), policy.getId())) {
            return Mono.error(AnomalyPolicyConflictException.duplicateScope());
        }
        policies.put(policy.getId(), policy);
        return Mono.just(policy);
    }

    @Override
    public synchronized Mono<AnomalyPolicy> findById(String id) {
        return Mono.justOrEmpty(policies.get(id));
    }

    @Override
    public synchronized Mono<AnomalyPolicy> findByScope(AnomalyPolicyScope scope, String service,
                                                        String environment, String operation) {
        String scopeKey = AnomalyPolicy.scopeKeyOf(scope, service, environment, operation);
        return policies.values().stream()
                .filter(policy -> policy.scopeKey().equals(scopeKey))
                .findFirst().map(Mono::just).orElseGet(Mono::empty);
    }

    @Override
    public synchronized Flux<AnomalyPolicy> findAll() {
        List<AnomalyPolicy> snapshot = policies.values().stream()
                .sorted(Comparator.comparing(AnomalyPolicy::getScope)
                        .thenComparing(AnomalyPolicy::getName))
                .toList();
        return Flux.fromIterable(snapshot);
    }

    private boolean hasScope(String scopeKey, String excludedId) {
        return policies.values().stream()
                .anyMatch(existing -> !existing.getId().equals(excludedId)
                        && existing.scopeKey().equals(scopeKey));
    }
}
