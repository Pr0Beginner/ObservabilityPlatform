package org.zmy.observabilityplatform.incident.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zmy.observabilityplatform.incident.application.notification.IncidentNotifier;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyDetectionSettings;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicy;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicyScope;
import org.zmy.observabilityplatform.incident.domain.model.Incident;
import org.zmy.observabilityplatform.incident.domain.model.IncidentStatus;
import org.zmy.observabilityplatform.incident.domain.model.MetricKey;
import org.zmy.observabilityplatform.incident.infrastructure.repository.memory.InMemoryIncidentNotificationRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.memory.InMemoryAnomalyPolicyRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.memory.InMemoryIncidentRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.memory.InMemoryIncidentTraceLinkRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.memory.InMemoryMetricTraceSampleRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.memory.InMemoryMetricWindowRepository;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AnomalyEvaluationServiceTest {
    private static final String SERVICE = "orders";
    private static final String ENVIRONMENT = "prod";
    private static final String OPERATION = "POST /orders";
    private static final Instant FIRST_WINDOW = Instant.parse("2026-09-19T10:00:00Z");

    private InMemoryMetricWindowRepository metrics;
    private InMemoryIncidentRepository incidents;
    private InMemoryIncidentTraceLinkRepository links;
    private InMemoryMetricTraceSampleRepository samples;
    private AnomalyEvaluationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-19T12:00:00Z"), ZoneOffset.UTC);
        metrics = new InMemoryMetricWindowRepository();
        incidents = new InMemoryIncidentRepository();
        links = new InMemoryIncidentTraceLinkRepository();
        samples = new InMemoryMetricTraceSampleRepository();
        IncidentNotifier notifier = (incident, type) -> Mono.empty();
        IncidentNotificationService notificationService = new IncidentNotificationService(
                new InMemoryIncidentNotificationRepository(), incidents, notifier, clock, 120);
        IncidentLifecycleService lifecycle = new IncidentLifecycleService(incidents, notificationService);
        InMemoryAnomalyPolicyRepository policies = new InMemoryAnomalyPolicyRepository();
        policies.create(AnomalyPolicy.create("orders-operation-policy", "Orders operation",
                AnomalyPolicyScope.OPERATION, SERVICE, ENVIRONMENT, OPERATION, true,
                new AnomalyDetectionSettings(3, 5, 2, 2.0, 0.5,
                        0.1, 0.05, 2.0, 1_440, 2), clock.instant())).block();
        AnomalyPolicyResolver resolver = new AnomalyPolicyResolver(policies);
        service = new AnomalyEvaluationService(
                metrics, incidents, lifecycle, samples, links, policies, resolver, clock);
    }

    @Test
    void opensRecoversAndReopensOneStableRequestSpikeIncident() {
        seedVolume(FIRST_WINDOW.minusSeconds(86_400), 10);
        seedVolume(FIRST_WINDOW, 25);
        samples.recordIfAbsent(total(), FIRST_WINDOW, "trace-spike").block();

        service.evaluate(FIRST_WINDOW).block();

        Incident opened = findSpike();
        assertThat(opened.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(opened.getCurrentValue()).isEqualTo(25.0);
        assertThat(opened.getPolicyReference().getPolicyId()).isEqualTo("orders-operation-policy");
        assertThat(opened.getPolicyReference().getPolicyVersion()).isEqualTo(1);
        assertThat(links.findTraceIdsByIncidentId(opened.getId(), 10).collectList().block())
                .containsExactly("trace-spike");

        Instant healthyOne = FIRST_WINDOW.plusSeconds(300);
        seedVolume(healthyOne.minusSeconds(86_400), 10);
        seedVolume(healthyOne, 10);
        service.evaluate(healthyOne).block();
        assertThat(findSpike().getHealthyWindowCount()).isEqualTo(1);

        Instant healthyTwo = healthyOne.plusSeconds(300);
        seedVolume(healthyTwo.minusSeconds(86_400), 10);
        seedVolume(healthyTwo, 10);
        service.evaluate(healthyTwo).block();
        Incident recovered = findSpike();
        assertThat(recovered.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(recovered.getCurrentValue()).isEqualTo(10.0);

        Instant recurrence = healthyTwo.plusSeconds(300);
        seedVolume(recurrence.minusSeconds(86_400), 10);
        seedVolume(recurrence, 30);
        service.evaluate(recurrence).block();

        Incident reopened = findSpike();
        assertThat(reopened.getId()).isEqualTo(opened.getId());
        assertThat(reopened.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(reopened.getHealthyWindowCount()).isZero();
    }

    private void seedVolume(Instant window, long count) {
        metrics.increment(total(), window, count).block();
    }

    private MetricKey total() {
        return MetricKey.requestTotal(SERVICE, ENVIRONMENT, OPERATION);
    }

    private Incident findSpike() {
        return incidents.findByDedupKey("REQUEST_VOLUME_SPIKE|orders|prod|POST /orders|*").block();
    }
}
