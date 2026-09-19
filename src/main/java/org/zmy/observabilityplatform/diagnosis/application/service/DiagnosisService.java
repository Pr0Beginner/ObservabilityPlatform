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
import org.zmy.observabilityplatform.diagnosis.domain.repository.DiagnosisRepository;
import org.zmy.observabilityplatform.incident.application.service.IncidentQueryService;
import org.zmy.observabilityplatform.shared.exception.NotFoundException;
import org.zmy.observabilityplatform.shared.exception.BusinessConflictException;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.time.Duration;
import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisTaskStatus;
import reactor.core.publisher.Flux;

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
        // 先确认事件存在，再禁止同一事件同时运行多个诊断任务。
        return incidentQueryService.findById(incidentId)
                .then(diagnosisRepository.findActiveByIncidentId(incidentId)
                        .flatMap(existing -> Mono.<DiagnosisTask>error(new BusinessConflictException(
                                "An active diagnosis already exists for this incident")))
                        // 历史任务保留版本序列，便于区分同一事件的多次诊断结果。
                        .switchIfEmpty(Mono.defer(() -> diagnosisRepository.findLatestByIncidentId(incidentId)
                                .map(latest -> latest.getVersion() + 1)
                                .defaultIfEmpty(1)
                                .flatMap(version -> createNew(incidentId, version)))))
                .map(DiagnosisTaskView::from);
    }

    private Mono<DiagnosisTask> createNew(String incidentId, int version) {
        Instant now = clock.instant();
        DiagnosisTask task = DiagnosisTask.request(UUID.randomUUID().toString(), incidentId, version, now);
        DiagnosisRequestedEvent event = new DiagnosisRequestedEvent(UUID.randomUUID().toString(), task.getId(),
                incidentId, task.getVersion(), now);
        // 任务持久化成功后再发布请求，避免消费者处理一个尚不可查询的任务。
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
        return diagnosisRepository.findTaskById(event.getTaskId())
                .switchIfEmpty(Mono.error(new NotFoundException("Diagnosis task not found: " + event.getTaskId())))
                .flatMap(task -> {
                    if (task.getVersion() != event.getVersion()
                            || !task.getIncidentId().equals(event.getIncidentId())) {
                        return Mono.error(new BusinessConflictException(
                                "Diagnosis result does not match the requested task version"));
                    }
                    if (!task.isActive()) {
                        return handleDuplicateResult(task, event);
                    }
                    // 下游显式返回错误时只更新任务状态，不生成不完整的报告。
                    if (event.getError() != null && !event.getError().isBlank()) {
                        return diagnosisRepository.saveTask(task.fail(event.getError(), clock.instant())).then();
                    }
                    // 报告落库后再标记任务成功，保证成功状态一定对应可查询的报告。
                    Instant completedAt = event.getCompletedAt() == null ? clock.instant() : event.getCompletedAt();
                    DiagnosisReport report = DiagnosisReport.generate(UUID.randomUUID().toString(), task.getId(),
                            event.getVersion(), event.getRootCause(), event.getConfidence(), event.getEvidence(),
                            event.getRecommendations(), event.getToolCalls(), completedAt);
                    return diagnosisRepository.saveReport(report)
                            .then(diagnosisRepository.saveTask(task.complete(report, clock.instant())))
                            .then();
                });
    }

    public Mono<DiagnosisTaskView> cancel(String taskId) {
        return findTask(taskId)
                .flatMap(task -> diagnosisRepository.saveTask(task.cancel(clock.instant())))
                .map(DiagnosisTaskView::from);
    }

    public Mono<DiagnosisTaskView> retry(String taskId) {
        return findTask(taskId).flatMap(task -> {
            if (task.isActive()) {
                return Mono.error(new BusinessConflictException("An active diagnosis cannot be retried"));
            }
            return create(task.getIncidentId());
        });
    }

    public Mono<Void> timeoutExpired(Duration timeout) {
        if (timeout.isNegative() || timeout.isZero()) {
            return Mono.error(new IllegalArgumentException("timeout must be positive"));
        }
        Instant cutoff = clock.instant().minus(timeout);
        return diagnosisRepository.findActiveUpdatedBefore(cutoff)
                .concatMap(task -> diagnosisRepository.saveTask(task.timeout(clock.instant())))
                .then();
    }

    private Mono<DiagnosisTask> findTask(String taskId) {
        return diagnosisRepository.findTaskById(taskId)
                .switchIfEmpty(Mono.error(new NotFoundException("Diagnosis task not found: " + taskId)));
    }

    private Mono<Void> handleDuplicateResult(DiagnosisTask task, DiagnosisCompletedEvent event) {
        if (task.getStatus() == DiagnosisTaskStatus.SUCCEEDED
                && (event.getError() == null || event.getError().isBlank())) {
            return diagnosisRepository.findReportByTaskId(task.getId())
                    .filter(report -> sameResult(report, event))
                    .switchIfEmpty(Mono.error(new BusinessConflictException(
                            "Duplicate diagnosis result conflicts with the stored report")))
                    .then();
        }
        if (task.getStatus() == DiagnosisTaskStatus.FAILED
                && event.getError() != null && event.getError().equals(task.getFailureReason())) {
            return Mono.empty();
        }
        return Mono.error(new BusinessConflictException(
                "Diagnosis task is already terminal: " + task.getStatus()));
    }

    private boolean sameResult(DiagnosisReport report, DiagnosisCompletedEvent event) {
        return report.getVersion() == event.getVersion()
                && report.getRootCause().equals(event.getRootCause())
                && Double.compare(report.getConfidence(), event.getConfidence()) == 0
                && report.getEvidence().equals(event.getEvidence())
                && report.getRecommendations().equals(event.getRecommendations())
                && report.getToolCalls().equals(event.getToolCalls());
    }
}
