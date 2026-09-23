package org.zmy.observabilityplatform.diagnosis.domain.repository;

import org.zmy.observabilityplatform.diagnosis.domain.event.DiagnosisRequestedEvent;
import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisReport;
import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisTask;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface DiagnosisRepository {
    /** Persists a new task and its requested event atomically. */
    Mono<DiagnosisTask> createTask(DiagnosisTask task, DiagnosisRequestedEvent event);

    /** Changes state only if the stored task still matches the expected active state. */
    Mono<DiagnosisTask> transition(DiagnosisTask expected, DiagnosisTask updated);

    /** Commits both the successful state transition and report, or neither. */
    Mono<DiagnosisTask> complete(DiagnosisTask expected, DiagnosisTask updated, DiagnosisReport report);

    Mono<DiagnosisTask> findTaskById(String id);

    Mono<DiagnosisTask> findActiveByIncidentId(String incidentId);

    Mono<DiagnosisTask> findLatestByIncidentId(String incidentId);

    Flux<DiagnosisTask> findByIncidentId(String incidentId, int limit);

    Mono<DiagnosisReport> findReportByTaskId(String taskId);

    Flux<DiagnosisTask> findActiveUpdatedBefore(Instant cutoff);
}
