package org.zmy.observabilityplatform.incident.domain.repository;

import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicy;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicyScope;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AnomalyPolicyRepository {
    Mono<AnomalyPolicy> create(AnomalyPolicy policy);

    Mono<AnomalyPolicy> update(AnomalyPolicy policy);

    Mono<AnomalyPolicy> findById(String id);

    Mono<AnomalyPolicy> findByScope(AnomalyPolicyScope scope, String service,
                                    String environment, String operation);

    Flux<AnomalyPolicy> findAll();
}
