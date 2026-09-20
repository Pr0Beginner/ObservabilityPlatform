package org.zmy.observabilityplatform.diagnosis.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zmy.observabilityplatform.diagnosis.application.dto.DiagnosisTaskView;
import org.zmy.observabilityplatform.diagnosis.domain.event.DiagnosisCompletedEvent;
import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisTask;
import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisTaskStatus;
import org.zmy.observabilityplatform.diagnosis.infrastructure.messaging.LocalDiagnosisRequestedPublisher;
import org.zmy.observabilityplatform.diagnosis.infrastructure.repository.memory.InMemoryDiagnosisRepository;
import org.zmy.observabilityplatform.incident.application.service.IncidentQueryService;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicyReference;
import org.zmy.observabilityplatform.incident.domain.model.Incident;
import org.zmy.observabilityplatform.incident.infrastructure.repository.memory.InMemoryIncidentRepository;
import org.zmy.observabilityplatform.shared.exception.BusinessConflictException;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.zmy.observabilityplatform.support.AuditTestFixture.auditTrail;

class DiagnosisServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-19T12:00:00Z");
    private InMemoryDiagnosisRepository repository;
    private DiagnosisService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryDiagnosisRepository();
        InMemoryIncidentRepository incidents = new InMemoryIncidentRepository();
        incidents.save(Incident.open("incident-1", "dedup-1", "orders", "prod", "fp-1",
                "ERROR", 3, NOW.minusSeconds(60), NOW.minusSeconds(30),
                new AnomalyPolicyReference("global-default", 1))).block();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new DiagnosisService(repository, new IncidentQueryService(incidents, incidents),
                new LocalDiagnosisRequestedPublisher(), clock, auditTrail(clock));
    }

    @Test
    void acceptsIdenticalResultOnceAndRetriesAsANewVersion() {
        DiagnosisTaskView task = service.create("incident-1").block();
        DiagnosisCompletedEvent result = result(task, "Database unavailable");

        service.complete(result).block();
        service.complete(result).block();

        DiagnosisTaskView retry = service.retry(task.getId()).block();
        assertThat(retry.getVersion()).isEqualTo(2);
        assertThat(retry.getStatus()).isEqualTo(DiagnosisTaskStatus.PENDING.name());

        DiagnosisCompletedEvent conflicting = result(task, "Different root cause");
        StepVerifier.create(service.complete(conflicting))
                .expectError(BusinessConflictException.class)
                .verify();
    }

    @Test
    void timesOutExpiredActiveTasks() {
        DiagnosisTask expired = DiagnosisTask.request("task-expired", "incident-1", 1,
                NOW.minus(Duration.ofMinutes(20)));
        repository.saveTask(expired).block();

        service.timeoutExpired(Duration.ofMinutes(10)).block();

        assertThat(repository.findTaskById(expired.getId()).block().getStatus())
                .isEqualTo(DiagnosisTaskStatus.TIMEOUT);
    }

    private DiagnosisCompletedEvent result(DiagnosisTaskView task, String rootCause) {
        return new DiagnosisCompletedEvent("event-1", task.getId(), "incident-1", task.getVersion(),
                rootCause, 0.9, List.of("evidence"), List.of("recommendation"),
                List.of("tool"), NOW, null);
    }
}
