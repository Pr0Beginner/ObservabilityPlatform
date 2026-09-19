package org.zmy.observabilityplatform.diagnosis.infrastructure.repository.memory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisReport;
import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisTask;
import org.zmy.observabilityplatform.diagnosis.domain.repository.DiagnosisRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "local", matchIfMissing = true)
public class InMemoryDiagnosisRepository implements DiagnosisRepository {
    private final Map<String, DiagnosisTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, DiagnosisReport> reportsByTask = new ConcurrentHashMap<>();

    @Override
    public Mono<DiagnosisTask> saveTask(DiagnosisTask task) {
        tasks.put(task.getId(), task);
        return Mono.just(task);
    }

    @Override
    public Mono<DiagnosisTask> findTaskById(String id) {
        return Mono.justOrEmpty(tasks.get(id));
    }

    @Override
    public Mono<DiagnosisTask> findActiveByIncidentId(String incidentId) {
        return Flux.fromIterable(tasks.values())
                .filter(task -> task.getIncidentId().equals(incidentId) && task.isActive())
                .next();
    }

    @Override
    public Mono<DiagnosisTask> findLatestByIncidentId(String incidentId) {
        return Flux.fromIterable(tasks.values())
                .filter(task -> task.getIncidentId().equals(incidentId))
                .sort(Comparator.comparingInt(DiagnosisTask::getVersion).reversed())
                .next();
    }

    @Override
    public Mono<DiagnosisReport> saveReport(DiagnosisReport report) {
        reportsByTask.put(report.getTaskId(), report);
        return Mono.just(report);
    }

    @Override
    public Mono<DiagnosisReport> findReportByTaskId(String taskId) {
        return Mono.justOrEmpty(reportsByTask.get(taskId));
    }

    @Override
    public Flux<DiagnosisTask> findActiveUpdatedBefore(Instant cutoff) {
        return Flux.fromIterable(tasks.values())
                .filter(DiagnosisTask::isActive)
                .filter(task -> task.getUpdatedAt().isBefore(cutoff));
    }
}
