package org.zmy.observabilityplatform.diagnosis.application.service;

import org.junit.jupiter.api.Test;
import org.zmy.observabilityplatform.diagnosis.domain.event.DiagnosisCompletedEvent;
import org.zmy.observabilityplatform.diagnosis.domain.event.DiagnosisRequestedEvent;
import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisReport;
import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisTask;
import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisTaskStatus;
import org.zmy.observabilityplatform.diagnosis.infrastructure.repository.memory.InMemoryDiagnosisRepository;
import org.zmy.observabilityplatform.incident.application.service.IncidentQueryService;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicyReference;
import org.zmy.observabilityplatform.incident.domain.model.Incident;
import org.zmy.observabilityplatform.incident.infrastructure.repository.memory.InMemoryIncidentRepository;
import org.zmy.observabilityplatform.shared.exception.BusinessConflictException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.zmy.observabilityplatform.support.AuditTestFixture.auditTrail;

class DiagnosisReliabilityTest {
    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");

    @Test
    void cancelledTaskCannotBeOverwrittenByAnInFlightCompletion() {
        var gate = Sinks.<Void>one();
        var repository = new InMemoryDiagnosisRepository() {
            @Override
            public Mono<DiagnosisTask> complete(DiagnosisTask expected, DiagnosisTask updated, DiagnosisReport report) {
                return gate.asMono().then(Mono.defer(() -> super.complete(expected, updated, report)));
            }
        };
        var service = service(repository);
        var task = service.create("incident").block();
        var event = new DiagnosisCompletedEvent("event", task.getId(), "incident", 1, "database timeout", 0.8,
                List.of("log: evidence"), List.of("inspect database"), List.of("context"), NOW, null);
        var completing = service.complete(event).toFuture();

        service.cancel(task.getId()).block();
        gate.tryEmitEmpty();

        assertThatThrownBy(completing::join).hasCauseInstanceOf(BusinessConflictException.class);
        assertThat(repository.findTaskById(task.getId()).block().getStatus()).isEqualTo(DiagnosisTaskStatus.CANCELLED);
        assertThat(repository.findReportByTaskId(task.getId()).block()).isNull();
        assertThat(repository.pending(10).collectList().block()).isEmpty();
    }

    @Test
    void failedDeliveryKeepsTheOriginalEventForRetryAndAcknowledgesOnlyAfterSuccess() {
        var repository = new InMemoryDiagnosisRepository();
        var task = service(repository).create("incident").block();
        AtomicBoolean fail = new AtomicBoolean(true);
        List<DiagnosisRequestedEvent> attempts = new ArrayList<>();
        var dispatcher = new DiagnosisRequestDispatcher(repository, event -> {
            attempts.add(event);
            return fail.getAndSet(false) ? Mono.error(new IllegalStateException("broker unavailable")) : Mono.empty();
        });

        dispatcher.dispatch(10).block();
        assertThat(repository.pending(10).count().block()).isEqualTo(1);
        assertThat(repository.findTaskById(task.getId()).block().isActive()).isTrue();
        dispatcher.dispatch(10).block();
        dispatcher.dispatch(10).block();

        assertThat(attempts).hasSize(2);
        assertThat(attempts.get(0)).isEqualTo(attempts.get(1));
        assertThat(repository.pending(10).count().block()).isZero();
    }

    @Test
    void taskCreationCannotOverwriteAnotherActiveTaskOrLeaveAnExtraRequest() {
        var repository = new InMemoryDiagnosisRepository();
        var first = DiagnosisTask.request("first", "incident", 1, NOW);
        var second = DiagnosisTask.request("second", "incident", 2, NOW);
        repository.createTask(first, requested(first)).block();

        assertThatThrownBy(() -> repository.createTask(second, requested(second)).block())
                .isInstanceOf(BusinessConflictException.class);
        assertThat(repository.findTaskById("first").block()).isEqualTo(first);
        assertThat(repository.findTaskById("second").block()).isNull();
        assertThat(repository.pending(10).count().block()).isEqualTo(1);
    }

    private DiagnosisRequestedEvent requested(DiagnosisTask task) {
        return new DiagnosisRequestedEvent("event-" + task.getId(), task.getId(), task.getIncidentId(),
                task.getVersion(), task.getCreatedAt());
    }

    private DiagnosisService service(InMemoryDiagnosisRepository repository) {
        var incidents = new InMemoryIncidentRepository();
        incidents.save(Incident.open("incident", "dedup", "orders", "test", "fp", "ERROR", 3,
                NOW.minusSeconds(60), NOW, new AnomalyPolicyReference("global-default", 1))).block();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new DiagnosisService(repository, new IncidentQueryService(incidents, incidents), clock, auditTrail(clock));
    }
}
