package org.zmy.observabilityplatform.incident.application.service;

import org.junit.jupiter.api.Test;
import org.zmy.observabilityplatform.incident.application.command.CreateAnomalyPolicyCommand;
import org.zmy.observabilityplatform.incident.application.command.UpdateAnomalyPolicyCommand;
import org.zmy.observabilityplatform.incident.application.dto.AnomalyPolicyView;
import org.zmy.observabilityplatform.incident.domain.exception.AnomalyPolicyConflictException;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicy;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicyScope;
import org.zmy.observabilityplatform.incident.infrastructure.repository.memory.InMemoryAnomalyPolicyRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnomalyPolicyServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");

    @Test
    void resolvesOperationThenServiceThenGlobalAndHonorsDisabledOverrides() {
        InMemoryAnomalyPolicyRepository repository = new InMemoryAnomalyPolicyRepository();
        repository.create(policy("service-policy", AnomalyPolicyScope.SERVICE,
                "orders", "prod", null, true)).block();
        repository.create(policy("operation-policy", AnomalyPolicyScope.OPERATION,
                "orders", "prod", "POST /orders", false)).block();
        AnomalyPolicyResolver resolver = new AnomalyPolicyResolver(repository);

        AnomalyPolicy operation = resolver.resolve("orders", "prod", "POST /orders").block();
        AnomalyPolicy service = resolver.resolve("orders", "prod", "GET /orders").block();
        AnomalyPolicy global = resolver.resolve("payments", "prod", "POST /payments").block();

        assertThat(operation.getId()).isEqualTo("operation-policy");
        assertThat(operation.isEnabled()).isFalse();
        assertThat(service.getId()).isEqualTo("service-policy");
        assertThat(global.getId()).isEqualTo(AnomalyPolicy.GLOBAL_DEFAULT_ID);
    }

    @Test
    void createsAndUpdatesWithScopeAndVersionProtection() {
        InMemoryAnomalyPolicyRepository repository = new InMemoryAnomalyPolicyRepository();
        AnomalyPolicyManagementService service = new AnomalyPolicyManagementService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));
        CreateAnomalyPolicyCommand create = createCommand("Orders", AnomalyPolicyScope.SERVICE,
                "orders", "prod", null, true);

        AnomalyPolicyView created = service.create(create).block();
        UpdateAnomalyPolicyCommand update = updateCommand(created.getVersion(), "Orders disabled",
                AnomalyPolicyScope.SERVICE, "orders", "prod", null, false);
        AnomalyPolicyView updated = service.update(created.getId(), update).block();

        assertThat(updated.getVersion()).isEqualTo(2);
        assertThat(updated.isEnabled()).isFalse();
        assertThatThrownBy(() -> service.update(created.getId(), update).block())
                .isInstanceOf(AnomalyPolicyConflictException.class);
        assertThatThrownBy(() -> service.create(create).block())
                .isInstanceOf(AnomalyPolicyConflictException.class);
    }

    private AnomalyPolicy policy(String id, AnomalyPolicyScope scope, String service,
                                 String environment, String operation, boolean enabled) {
        return AnomalyPolicy.create(id, id, scope, service, environment, operation,
                enabled, org.zmy.observabilityplatform.incident.domain.model.AnomalyDetectionSettings.defaults(),
                NOW);
    }

    private CreateAnomalyPolicyCommand createCommand(String name, AnomalyPolicyScope scope,
                                                      String service, String environment,
                                                      String operation, boolean enabled) {
        return new CreateAnomalyPolicyCommand(name, scope, service, environment, operation, enabled,
                3, 20, 5, 2.0, 0.5, 0.1, 0.05, 2.0, 1_440, 2);
    }

    private UpdateAnomalyPolicyCommand updateCommand(long version, String name, AnomalyPolicyScope scope,
                                                      String service, String environment,
                                                      String operation, boolean enabled) {
        return new UpdateAnomalyPolicyCommand(version, name, scope, service, environment, operation, enabled,
                3, 20, 5, 2.0, 0.5, 0.1, 0.05, 2.0, 1_440, 2);
    }
}
