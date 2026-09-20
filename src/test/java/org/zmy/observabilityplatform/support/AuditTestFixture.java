package org.zmy.observabilityplatform.support;

import org.zmy.observabilityplatform.audit.application.service.AuditTrailService;
import org.zmy.observabilityplatform.audit.domain.model.AuditActor;
import org.zmy.observabilityplatform.audit.infrastructure.repository.memory.InMemoryAuditRepository;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.List;

public final class AuditTestFixture {
    private AuditTestFixture() {
    }

    public static AuditTrailService auditTrail(Clock clock) {
        return new AuditTrailService(new InMemoryAuditRepository(),
                () -> Mono.just(new AuditActor("test-operator", List.of("OPERATOR"))), clock);
    }
}
