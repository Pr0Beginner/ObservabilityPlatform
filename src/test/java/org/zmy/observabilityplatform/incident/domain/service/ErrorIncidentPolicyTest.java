package org.zmy.observabilityplatform.incident.domain.service;

import org.junit.jupiter.api.Test;
import org.zmy.observabilityplatform.shared.exception.BusinessConflictException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ErrorIncidentPolicyTest {
    private final ErrorIncidentPolicy policy = new ErrorIncidentPolicy(3);

    @Test
    void opensIncidentOnlyAfterTheConfiguredErrorThreshold() {
        Instant occurredAt = Instant.parse("2026-09-18T08:30:45Z");
        Instant window = policy.windowOf(occurredAt);

        assertThat(policy.observes("ERROR")).isTrue();
        assertThat(policy.observes("WARN")).isFalse();
        assertThat(policy.hasReachedThreshold(2)).isFalse();
        assertThat(policy.hasReachedThreshold(3)).isTrue();
        assertThat(policy.dedupKey("orders", "prod", "fp-1", window))
                .isEqualTo("orders|prod|fp-1|2026-09-18T08:30:00Z");
        assertThatThrownBy(() -> policy.openIncident("incident-1", "dedup-1", "orders", "prod",
                "fp-1", "ERROR", 2, window, occurredAt))
                .isInstanceOf(BusinessConflictException.class);
    }
}
