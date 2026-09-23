package org.zmy.observabilityplatform.diagnosis.infrastructure.repository.memory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.diagnosis.application.publisher.DiagnosisRequestOutbox;
import org.zmy.observabilityplatform.diagnosis.domain.event.DiagnosisRequestedEvent;
import org.zmy.observabilityplatform.diagnosis.domain.exception.DiagnosisStateConflictException;
import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisReport;
import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisTask;
import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisTaskStatus;
import org.zmy.observabilityplatform.diagnosis.domain.repository.DiagnosisRepository;
import org.zmy.observabilityplatform.shared.exception.BusinessConflictException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "local", matchIfMissing = true)
public class InMemoryDiagnosisRepository implements DiagnosisRepository, DiagnosisRequestOutbox {
    private final Map<String, DiagnosisTask> tasks = new HashMap<>();
    private final Map<String, DiagnosisReport> reportsByTask = new HashMap<>();
    private final Map<String, DiagnosisRequestedEvent> outbox = new HashMap<>();
    private final Object monitor = new Object();

    @Override
    public Mono<DiagnosisTask> createTask(DiagnosisTask task, DiagnosisRequestedEvent event) {
        return Mono.fromCallable(() -> {
            synchronized (monitor) {
                if (tasks.containsKey(task.getId()) || outbox.containsKey(event.getEventId())
                        || tasks.values().stream().anyMatch(existing -> existing.getIncidentId().equals(task.getIncidentId())
                        && (existing.isActive() || existing.getVersion() == task.getVersion()))) {
                    throw new BusinessConflictException("An active diagnosis or task version already exists");
                }
                tasks.put(task.getId(), task);
                outbox.put(event.getEventId(), event);
                return task;
            }
        });
    }

    @Override
    public Mono<DiagnosisTask> transition(DiagnosisTask expected, DiagnosisTask updated) {
        return Mono.fromCallable(() -> {
            synchronized (monitor) {
                requireExpected(expected);
                tasks.put(updated.getId(), updated);
                removeTerminalRequest(updated);
                return updated;
            }
        });
    }

    @Override
    public Mono<DiagnosisTask> complete(DiagnosisTask expected, DiagnosisTask updated, DiagnosisReport report) {
        return Mono.fromCallable(() -> {
            synchronized (monitor) {
                requireExpected(expected);
                if (updated.getStatus() != DiagnosisTaskStatus.SUCCEEDED
                        || !updated.getId().equals(report.getTaskId()) || updated.getVersion() != report.getVersion()) {
                    throw new IllegalArgumentException("Report must match the completed task");
                }
                reportsByTask.put(updated.getId(), report);
                tasks.put(updated.getId(), updated);
                removeTerminalRequest(updated);
                return updated;
            }
        });
    }

    private void requireExpected(DiagnosisTask expected) {
        if (!expected.isActive() || !expected.equals(tasks.get(expected.getId()))) {
            throw new DiagnosisStateConflictException(expected.getId());
        }
    }

    private void removeTerminalRequest(DiagnosisTask task) {
        if (!task.isActive()) {
            outbox.values().removeIf(event -> event.getTaskId().equals(task.getId()));
        }
    }

    @Override
    public Mono<DiagnosisTask> findTaskById(String id) {
        return Mono.fromCallable(() -> {
            synchronized (monitor) {
                return tasks.get(id);
            }
        });
    }

    private List<DiagnosisTask> snapshot() {
        synchronized (monitor) {
            return List.copyOf(tasks.values());
        }
    }

    @Override
    public Mono<DiagnosisTask> findActiveByIncidentId(String incidentId) {
        return Flux.defer(() -> Flux.fromIterable(snapshot()))
                .filter(task -> task.getIncidentId().equals(incidentId) && task.isActive()).next();
    }

    @Override
    public Mono<DiagnosisTask> findLatestByIncidentId(String incidentId) {
        return findByIncidentId(incidentId, 1).next();
    }

    @Override
    public Flux<DiagnosisTask> findByIncidentId(String incidentId, int limit) {
        return Flux.defer(() -> Flux.fromIterable(snapshot()))
                .filter(task -> task.getIncidentId().equals(incidentId))
                .sort(Comparator.comparingInt(DiagnosisTask::getVersion).reversed()).take(limit);
    }

    @Override
    public Mono<DiagnosisReport> findReportByTaskId(String taskId) {
        return Mono.fromCallable(() -> {
            synchronized (monitor) {
                return reportsByTask.get(taskId);
            }
        });
    }

    @Override
    public Flux<DiagnosisTask> findActiveUpdatedBefore(Instant cutoff) {
        return Flux.defer(() -> Flux.fromIterable(snapshot()))
                .filter(DiagnosisTask::isActive).filter(task -> task.getUpdatedAt().isBefore(cutoff));
    }

    @Override
    public Flux<DiagnosisRequestedEvent> pending(int limit) {
        return Flux.defer(() -> {
            synchronized (monitor) {
                return Flux.fromIterable(outbox.values().stream()
                        .sorted(Comparator.comparing(DiagnosisRequestedEvent::getRequestedAt)
                                .thenComparing(DiagnosisRequestedEvent::getEventId)).limit(limit).toList());
            }
        });
    }

    @Override
    public Mono<Void> acknowledge(String eventId) {
        return Mono.fromRunnable(() -> {
            synchronized (monitor) {
                outbox.remove(eventId);
            }
        });
    }
}
