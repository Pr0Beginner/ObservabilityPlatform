package org.zmy.observabilityplatform.incident.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentTest {
    private static final Instant STARTED_AT = Instant.parse("2026-09-18T08:30:00Z");

    @Test
    void protectsStatusTransitionsAndClosingResolution() {
        Incident incident = Incident.open("incident-1", "dedup-1", "orders", "prod", "fp-1",
                "ERROR", 3, STARTED_AT, STARTED_AT.plusSeconds(10));

        assertThatThrownBy(() -> incident.transitionTo(
                IncidentStatus.CLOSED, null, STARTED_AT.plusSeconds(20)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resolution");

        Incident closed = incident.transitionTo(
                IncidentStatus.CLOSED, "Database connection restored", STARTED_AT.plusSeconds(20));

        assertThat(closed.getStatus()).isEqualTo(IncidentStatus.CLOSED);
        assertThat(closed.getResolution()).isEqualTo("Database connection restored");
    }

    @Test
    void neverDecreasesObservedErrorCount() {
        Incident incident = Incident.open("incident-1", "dedup-1", "orders", "prod", "fp-1",
                "ERROR", 3, STARTED_AT, STARTED_AT);

        Incident updated = incident.registerOccurrences(2, STARTED_AT.plusSeconds(30));

        assertThat(updated.getErrorCount()).isEqualTo(3);
    }
}
