package org.zmy.observabilityplatform.diagnosis.domain.repository;

import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisReport;
import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisTask;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.time.Instant;

public interface DiagnosisRepository {
    Mono<DiagnosisTask> saveTask(DiagnosisTask task);

    Mono<DiagnosisTask> findTaskById(String id);

    Mono<DiagnosisTask> findActiveByIncidentId(String incidentId);

    Mono<DiagnosisTask> findLatestByIncidentId(String incidentId);

    Mono<DiagnosisReport> saveReport(DiagnosisReport report);

    Mono<DiagnosisReport> findReportByTaskId(String taskId);

    Flux<DiagnosisTask> findActiveUpdatedBefore(Instant cutoff);
}
