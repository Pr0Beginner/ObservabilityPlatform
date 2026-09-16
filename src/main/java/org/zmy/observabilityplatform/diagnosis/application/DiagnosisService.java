package org.zmy.observabilityplatform.diagnosis.application;

import org.springframework.stereotype.Service;
import org.zmy.observabilityplatform.diagnosis.domain.DiagnosisCompletedEvent;
import org.zmy.observabilityplatform.diagnosis.domain.DiagnosisReport;
import org.zmy.observabilityplatform.diagnosis.domain.DiagnosisRepository;
import org.zmy.observabilityplatform.diagnosis.domain.DiagnosisRequestedEvent;
import org.zmy.observabilityplatform.diagnosis.domain.DiagnosisTask;
import org.zmy.observabilityplatform.diagnosis.domain.DiagnosisTaskStatus;
import org.zmy.observabilityplatform.incident.domain.IncidentRepository;
import org.zmy.observabilityplatform.support.NotFoundException;
import org.zmy.observabilityplatform.support.messaging.PlatformEventPublisher;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
public class DiagnosisService {
    private final DiagnosisRepository diagnosisRepository;
    private final IncidentRepository incidentRepository;
    private final PlatformEventPublisher eventPublisher;

    public DiagnosisService(DiagnosisRepository diagnosisRepository,
                            IncidentRepository incidentRepository,
                            PlatformEventPublisher eventPublisher) {
        this.diagnosisRepository = diagnosisRepository;
        this.incidentRepository = incidentRepository;
        this.eventPublisher = eventPublisher;
    }

    public Mono<DiagnosisTask> create(String incidentId) {
        return incidentRepository.findById(incidentId)
                .switchIfEmpty(Mono.error(new NotFoundException("Incident not found: " + incidentId)))
                .then(diagnosisRepository.findActiveByIncidentId(incidentId)
                        .flatMap(existing -> Mono.<DiagnosisTask>error(
                                new IllegalStateException("An active diagnosis already exists for this incident")))
                        .switchIfEmpty(Mono.defer(() -> diagnosisRepository.findLatestByIncidentId(incidentId)
                                .map(latest -> latest.version() + 1)
                                .defaultIfEmpty(1)
                                .flatMap(version -> createNew(incidentId, version)))));
    }

    private Mono<DiagnosisTask> createNew(String incidentId, int version) {
        Instant now = Instant.now();
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
                        .map(report -> new DiagnosisView(task, report))
                        .defaultIfEmpty(new DiagnosisView(task, null)));
    }

    public Mono<Void> complete(DiagnosisCompletedEvent event) {
        return diagnosisRepository.findTaskById(event.taskId())
                .switchIfEmpty(Mono.error(new NotFoundException("Diagnosis task not found: " + event.taskId())))
                .flatMap(task -> {
                    if (event.error() != null && !event.error().isBlank()) {
                        return diagnosisRepository.saveTask(task.withStatus(DiagnosisTaskStatus.FAILED, event.error())).then();
                    }
                    DiagnosisReport report = new DiagnosisReport(UUID.randomUUID().toString(), task.id(),
                            event.version(), event.rootCause(), event.confidence(), event.evidence(),
                            event.recommendations(), event.toolCalls(),
                            event.completedAt() == null ? Instant.now() : event.completedAt());
                    return diagnosisRepository.saveReport(report)
                            .then(diagnosisRepository.saveTask(task.withStatus(DiagnosisTaskStatus.SUCCEEDED, null)))
                            .then();
                });
    }

    public record DiagnosisView(DiagnosisTask task, DiagnosisReport report) { }
}
