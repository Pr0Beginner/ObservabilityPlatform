package org.zmy.observabilityplatform.diagnosis.domain;

import reactor.core.publisher.Mono;

public interface DiagnosisRepository {
    Mono<DiagnosisTask> saveTask(DiagnosisTask task);

    Mono<DiagnosisTask> findTaskById(String id);

    Mono<DiagnosisTask> findActiveByIncidentId(String incidentId);

    Mono<DiagnosisTask> findLatestByIncidentId(String incidentId);

    Mono<DiagnosisReport> saveReport(DiagnosisReport report);

    Mono<DiagnosisReport> findReportByTaskId(String taskId);
}
