package org.zmy.observabilityplatform.diagnosis.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.diagnosis.domain.DiagnosisReport;
import org.zmy.observabilityplatform.diagnosis.domain.DiagnosisRepository;
import org.zmy.observabilityplatform.diagnosis.domain.DiagnosisTask;
import org.zmy.observabilityplatform.diagnosis.domain.DiagnosisTaskStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.EnumSet;
import java.util.Map;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "local", matchIfMissing = true)
public class InMemoryDiagnosisRepository implements DiagnosisRepository {
    private static final EnumSet<DiagnosisTaskStatus> ACTIVE = EnumSet.of(
            DiagnosisTaskStatus.PENDING, DiagnosisTaskStatus.RUNNING);
    private final Map<String, DiagnosisTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, DiagnosisReport> reportsByTask = new ConcurrentHashMap<>();

    @Override
    public Mono<DiagnosisTask> saveTask(DiagnosisTask task) {
        tasks.put(task.id(), task);
        return Mono.just(task);
    }

    @Override
    public Mono<DiagnosisTask> findTaskById(String id) {
        return Mono.justOrEmpty(tasks.get(id));
    }

    @Override
    public Mono<DiagnosisTask> findActiveByIncidentId(String incidentId) {
        return Flux.fromIterable(tasks.values())
                .filter(task -> task.incidentId().equals(incidentId) && ACTIVE.contains(task.status()))
                .next();
    }

    @Override
    public Mono<DiagnosisTask> findLatestByIncidentId(String incidentId) {
        return Flux.fromIterable(tasks.values())
                .filter(task -> task.incidentId().equals(incidentId))
                .sort(Comparator.comparingInt(DiagnosisTask::version).reversed())
                .next();
    }

    @Override
    public Mono<DiagnosisReport> saveReport(DiagnosisReport report) {
        reportsByTask.put(report.taskId(), report);
        return Mono.just(report);
    }

    @Override
    public Mono<DiagnosisReport> findReportByTaskId(String taskId) {
        return Mono.justOrEmpty(reportsByTask.get(taskId));
    }
}
