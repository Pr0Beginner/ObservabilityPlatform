package org.zmy.observabilityplatform.diagnosis.application.service;

import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.diagnosis.application.dto.DiagnosisReportView;
import org.zmy.observabilityplatform.diagnosis.application.dto.DiagnosisTaskView;
import org.zmy.observabilityplatform.diagnosis.application.dto.DiagnosisView;
import org.zmy.observabilityplatform.diagnosis.application.publisher.DiagnosisRequestedPublisher;
import org.zmy.observabilityplatform.diagnosis.domain.event.DiagnosisCompletedEvent;
import org.zmy.observabilityplatform.diagnosis.domain.event.DiagnosisRequestedEvent;
import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisReport;
import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisTask;
import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisTaskStatus;
import org.zmy.observabilityplatform.diagnosis.domain.repository.DiagnosisRepository;
import org.zmy.observabilityplatform.incident.application.service.IncidentQueryService;
import org.zmy.observabilityplatform.shared.exception.NotFoundException;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class DiagnosisService {
    private final DiagnosisRepository diagnosisRepository;
    private final IncidentQueryService incidentQueryService;
    private final DiagnosisRequestedPublisher eventPublisher;
    private final Clock clock;

    public DiagnosisService(DiagnosisRepository diagnosisRepository,
                            IncidentQueryService incidentQueryService,
                            DiagnosisRequestedPublisher eventPublisher,
                            Clock clock) {
        this.diagnosisRepository = diagnosisRepository;
        this.incidentQueryService = incidentQueryService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public Mono<DiagnosisTaskView> create(String incidentId) {
        return incidentQueryService.findById(incidentId)
                .then(diagnosisRepository.findActiveByIncidentId(incidentId)
                        .flatMap(existing -> Mono.<DiagnosisTask>error(
                                new IllegalStateException("An active diagnosis already exists for this incident")))
                        .switchIfEmpty(Mono.defer(() -> diagnosisRepository.findLatestByIncidentId(incidentId)
                                .map(latest -> latest.version() + 1)
                                .defaultIfEmpty(1)
                                .flatMap(version -> createNew(incidentId, version)))))
                .map(DiagnosisTaskView::from);
    }

    private Mono<DiagnosisTask> createNew(String incidentId, int version) {
        Instant now = clock.instant();
        DiagnosisTask task = new DiagnosisTask(UUID.randomUUID().toString(), incidentId, version,
                DiagnosisTaskStatus.PENDING, now, now, null);
        DiagnosisRequestedEvent event = new DiagnosisRequestedEvent(UUID.randomUUID().toString(), task.id(),
                incidentId, task.version(), now);
        return diagnosisRepository.saveTask(task)
                .flatMap(saved -> eventPublisher.publish(event).thenReturn(saved));
    }

    public Mono<DiagnosisView> findById(String taskId) {
        return diagnosisRepository.findTaskById(taskId)
                .switchIfEmpty(Mono.error(new NotFoundException("Diagnosis task not found: " + taskId)))
                .flatMap(task -> diagnosisRepository.findReportByTaskId(taskId)
                        .map(report -> new DiagnosisView(DiagnosisTaskView.from(task), DiagnosisReportView.from(report)))
                        .defaultIfEmpty(new DiagnosisView(DiagnosisTaskView.from(task), null)));
    }

    public Mono<Void> complete(DiagnosisCompletedEvent event) {
        return diagnosisRepository.findTaskById(event.taskId())
                .switchIfEmpty(Mono.error(new NotFoundException("Diagnosis task not found: " + event.taskId())))
                .flatMap(task -> {
                    if (event.error() != null && !event.error().isBlank()) {
                        return diagnosisRepository.saveTask(task.withStatus(
                                DiagnosisTaskStatus.FAILED, event.error(), clock.instant())).then();
                    }
                    DiagnosisReport report = new DiagnosisReport(UUID.randomUUID().toString(), task.id(),
                            event.version(), event.rootCause(), event.confidence(), event.evidence(),
                            event.recommendations(), event.toolCalls(),
                            event.completedAt() == null ? clock.instant() : event.completedAt());
                    return diagnosisRepository.saveReport(report)
                            .then(diagnosisRepository.saveTask(task.withStatus(
                                    DiagnosisTaskStatus.SUCCEEDED, null, clock.instant())))
                            .then();
                });
    }
}
