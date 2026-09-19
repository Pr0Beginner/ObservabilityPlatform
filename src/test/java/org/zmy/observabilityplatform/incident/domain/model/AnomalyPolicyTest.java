package org.zmy.observabilityplatform.incident.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnomalyPolicyTest {
    private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");

    @Test
    void validatesScopeAndAdvancesVersionWhenRevised() {
        assertThatThrownBy(() -> AnomalyPolicy.create("policy-1", "Invalid service policy",
                AnomalyPolicyScope.SERVICE, "orders", null, null, true,
                AnomalyDetectionSettings.defaults(), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("environment");

        AnomalyPolicy policy = AnomalyPolicy.create("policy-1", "Orders",
                AnomalyPolicyScope.SERVICE, "orders", "prod", null, true,
                AnomalyDetectionSettings.defaults(), NOW);
        AnomalyPolicy revised = policy.revise("Orders disabled", policy.getScope(),
                policy.getService(), policy.getEnvironment(), null, false,
                policy.getSettings(), NOW.plusSeconds(1));

        assertThat(revised.getVersion()).isEqualTo(2);
        assertThat(revised.isEnabled()).isFalse();
        assertThat(revised.scopeKey()).isEqualTo(policy.scopeKey());
    }

    @Test
    void ownsTheAnomalyDecisionRules() {
        AnomalyDetectionSettings settings = new AnomalyDetectionSettings(
                3, 20, 5, 2.0, 0.5, 0.1, 0.05, 2.0, 1_440, 2);

        assertThat(settings.isRepeatedError(3)).isTrue();
        assertThat(settings.isRequestVolumeSpike(220, 100)).isTrue();
        assertThat(settings.isRequestVolumeDrop(40, 100)).isTrue();
        assertThat(settings.isNoTraffic(0, 100)).isTrue();
        assertThat(settings.isFailureRateSpike(30, 100, 5, 100)).isTrue();
        assertThat(settings.isErrorCodeCountSpike(10, 2)).isTrue();
        assertThat(settings.isErrorCodeRateSpike(10, 100, 2, 100)).isTrue();
        assertThat(settings.baselineWindowOf(NOW)).isEqualTo(NOW.minusSeconds(86_400));
    }

    @Test
    void keepsTheRequiredGlobalDefaultScope() {
        AnomalyPolicy policy = AnomalyPolicy.globalDefault(NOW);

        assertThatThrownBy(() -> policy.revise("Moved default", AnomalyPolicyScope.SERVICE,
                "orders", "prod", null, true, AnomalyDetectionSettings.defaults(), NOW.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scope cannot be changed");
    }
}
