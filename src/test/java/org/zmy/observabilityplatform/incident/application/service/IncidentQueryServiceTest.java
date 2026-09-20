package org.zmy.observabilityplatform.incident.application.service;

import org.junit.jupiter.api.Test;
import org.zmy.observabilityplatform.incident.application.query.IncidentSearchQuery;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicyReference;
import org.zmy.observabilityplatform.incident.domain.model.Incident;
import org.zmy.observabilityplatform.incident.infrastructure.repository.memory.InMemoryIncidentRepository;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentQueryServiceTest {
    @Test
    void filtersAndPaginatesIncidentsWithStableTotals() {
        InMemoryIncidentRepository repository = new InMemoryIncidentRepository();
        repository.save(incident("incident-1", "orders", "prod", Instant.parse("2026-09-20T10:00:00Z"))).block();
        repository.save(incident("incident-2", "orders", "prod", Instant.parse("2026-09-20T11:00:00Z"))).block();
        repository.save(incident("incident-3", "payments", "prod", Instant.parse("2026-09-20T12:00:00Z"))).block();
        IncidentQueryService service = new IncidentQueryService(repository, repository);

        var result = service.search(new IncidentSearchQuery(null, null, "orders", "prod", null,
                Instant.parse("2026-09-20T09:00:00Z"), Instant.parse("2026-09-20T12:00:00Z"),
                1, 1)).block();

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getItems()).singleElement()
                .satisfies(incident -> assertThat(incident.getId()).isEqualTo("incident-1"));
    }

    private Incident incident(String id, String service, String environment, Instant startedAt) {
        return Incident.open(id, "dedup-" + id, service, environment, "fingerprint-" + id,
                "ERROR", 3, startedAt, startedAt,
                new AnomalyPolicyReference("global-default", 1));
    }
}
