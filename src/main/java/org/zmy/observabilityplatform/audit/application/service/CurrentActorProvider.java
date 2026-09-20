package org.zmy.observabilityplatform.audit.application.service;

import org.zmy.observabilityplatform.audit.domain.model.AuditActor;
import reactor.core.publisher.Mono;

public interface CurrentActorProvider {
    Mono<AuditActor> currentActor();
}
