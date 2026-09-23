package org.zmy.observabilityplatform.diagnosis.infrastructure.repository.mysql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.Row;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class MySqlDiagnosisRepository implements DiagnosisRepository, DiagnosisRequestOutbox {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private final DatabaseClient databaseClient;
    private final ObjectMapper objectMapper;
    private final TransactionalOperator transactions;

    public MySqlDiagnosisRepository(DatabaseClient databaseClient, ObjectMapper objectMapper) {
        this.databaseClient = databaseClient;
        this.objectMapper = objectMapper;
        this.transactions = TransactionalOperator.create(new R2dbcTransactionManager(databaseClient.getConnectionFactory()));
    }

    @Override
    public Mono<DiagnosisTask> createTask(DiagnosisTask task, DiagnosisRequestedEvent event) {
        Mono<Void> insertTask = databaseClient.sql("""
                        INSERT INTO diagnosis_tasks (id, incident_id, version, status, created_at, updated_at, failure_reason)
                        VALUES (:id, :incidentId, :version, :status, :createdAt, :updatedAt, :failureReason)
                        """)
                .bind("id", task.getId()).bind("incidentId", task.getIncidentId()).bind("version", task.getVersion())
                .bind("status", task.getStatus().name()).bind("createdAt", toDatabaseTime(task.getCreatedAt()))
                .bind("updatedAt", toDatabaseTime(task.getUpdatedAt()))
                .bind("failureReason", task.getFailureReason() == null ? "" : task.getFailureReason())
                .fetch().rowsUpdated().then();
        Mono<Void> insertRequest = databaseClient.sql("""
                        INSERT INTO diagnosis_request_outbox (event_id, task_id, incident_id, version, requested_at)
                        VALUES (:eventId, :taskId, :incidentId, :version, :requestedAt)
                        """)
                .bind("eventId", event.getEventId()).bind("taskId", task.getId())
                .bind("incidentId", task.getIncidentId()).bind("version", task.getVersion())
                .bind("requestedAt", toDatabaseTime(event.getRequestedAt())).fetch().rowsUpdated().then();
        return transactions.transactional(insertTask.then(insertRequest))
                .then(findTaskById(task.getId()))
                .onErrorMap(DuplicateKeyException.class,
                        error -> new BusinessConflictException("An active diagnosis or task version already exists"));
    }

    @Override
    public Mono<DiagnosisTask> transition(DiagnosisTask expected, DiagnosisTask updated) {
        return transactions.transactional(updateState(expected, updated).then(removeTerminalRequest(updated)))
                .thenReturn(updated);
    }

    @Override
    public Mono<DiagnosisTask> complete(DiagnosisTask expected, DiagnosisTask updated, DiagnosisReport report) {
        if (updated.getStatus() != DiagnosisTaskStatus.SUCCEEDED || !updated.getId().equals(report.getTaskId())
                || updated.getVersion() != report.getVersion()) {
            return Mono.error(new IllegalArgumentException("Report must match the completed task"));
        }
        return transactions.transactional(updateState(expected, updated)
                        .then(Mono.defer(() -> insertReport(report))).then(removeTerminalRequest(updated)))
                .thenReturn(updated);
    }

    private Mono<Void> updateState(DiagnosisTask expected, DiagnosisTask updated) {
        if (!expected.isActive()) {
            return Mono.error(new DiagnosisStateConflictException(expected.getId()));
        }
        // States are monotonic within a task: comparing its original state prevents terminal overwrites.
        return databaseClient.sql("""
                        UPDATE diagnosis_tasks SET status = :status, updated_at = :updatedAt, failure_reason = :reason
                        WHERE id = :id AND incident_id = :incidentId AND version = :version AND status = :expectedStatus
                        """)
                .bind("status", updated.getStatus().name()).bind("updatedAt", toDatabaseTime(updated.getUpdatedAt()))
                .bind("reason", updated.getFailureReason() == null ? "" : updated.getFailureReason())
                .bind("id", expected.getId()).bind("incidentId", expected.getIncidentId())
                .bind("version", expected.getVersion()).bind("expectedStatus", expected.getStatus().name())
                .fetch().rowsUpdated().flatMap(rows -> rows == 1 ? Mono.empty()
                        : Mono.error(new DiagnosisStateConflictException(expected.getId())));
    }

    private Mono<Void> removeTerminalRequest(DiagnosisTask task) {
        return task.isActive() ? Mono.empty() : databaseClient.sql(
                        "DELETE FROM diagnosis_request_outbox WHERE task_id = :id")
                .bind("id", task.getId()).fetch().rowsUpdated().then();
    }

    @Override
    public Flux<DiagnosisRequestedEvent> pending(int limit) {
        return databaseClient.sql("""
                        SELECT event_id, task_id, incident_id, version, requested_at
                        FROM diagnosis_request_outbox ORDER BY requested_at, event_id LIMIT :limit
                        """)
                .bind("limit", limit).map((row, metadata) -> new DiagnosisRequestedEvent(
                        row.get("event_id", String.class), row.get("task_id", String.class),
                        row.get("incident_id", String.class), number(row.get("version", Integer.class)),
                        toInstant(row, "requested_at"))).all();
    }

    @Override
    public Mono<Void> acknowledge(String eventId) {
        return databaseClient.sql("DELETE FROM diagnosis_request_outbox WHERE event_id = :id")
                .bind("id", eventId).fetch().rowsUpdated().then();
    }

    @Override
    public Mono<DiagnosisTask> findTaskById(String id) {
        return databaseClient.sql("SELECT * FROM diagnosis_tasks WHERE id = :id")
                .bind("id", id).map((row, metadata) -> mapTask(row)).one();
    }

    @Override
    public Mono<DiagnosisTask> findActiveByIncidentId(String incidentId) {
        return databaseClient.sql("""
                        SELECT * FROM diagnosis_tasks
                        WHERE incident_id = :incidentId AND status IN ('PENDING', 'RUNNING')
                        ORDER BY created_at DESC LIMIT 1
                        """)
                .bind("incidentId", incidentId).map((row, metadata) -> mapTask(row)).one();
    }

    @Override
    public Mono<DiagnosisTask> findLatestByIncidentId(String incidentId) {
        return databaseClient.sql("SELECT * FROM diagnosis_tasks WHERE incident_id = :incidentId ORDER BY version DESC LIMIT 1")
                .bind("incidentId", incidentId).map((row, metadata) -> mapTask(row)).one();
    }

    @Override
    public Flux<DiagnosisTask> findByIncidentId(String incidentId, int limit) {
        return databaseClient.sql("""
                        SELECT * FROM diagnosis_tasks
                        WHERE incident_id = :incidentId
                        ORDER BY version DESC LIMIT :limit
                        """)
                .bind("incidentId", incidentId)
                .bind("limit", limit)
                .map((row, metadata) -> mapTask(row)).all();
    }

    private Mono<Void> insertReport(DiagnosisReport report) {
        try {
            return databaseClient.sql("""
                            INSERT INTO diagnosis_reports
                                (id, task_id, version, root_cause, confidence, evidence, recommendations, tool_calls, generated_at)
                            VALUES (:id, :taskId, :version, :rootCause, :confidence, :evidence, :recommendations, :toolCalls, :generatedAt)
                            """)
                    .bind("id", report.getId()).bind("taskId", report.getTaskId()).bind("version", report.getVersion())
                    .bind("rootCause", report.getRootCause()).bind("confidence", report.getConfidence())
                    .bind("evidence", objectMapper.writeValueAsString(report.getEvidence()))
                    .bind("recommendations", objectMapper.writeValueAsString(report.getRecommendations()))
                    .bind("toolCalls", objectMapper.writeValueAsString(report.getToolCalls()))
                    .bind("generatedAt", toDatabaseTime(report.getGeneratedAt()))
                    .fetch().rowsUpdated().then();
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }
    }

    @Override
    public Mono<DiagnosisReport> findReportByTaskId(String taskId) {
        return databaseClient.sql("SELECT * FROM diagnosis_reports WHERE task_id = :taskId ORDER BY version DESC LIMIT 1")
                .bind("taskId", taskId).map((row, metadata) -> mapReport(row)).one();
    }

    @Override
    public Flux<DiagnosisTask> findActiveUpdatedBefore(Instant cutoff) {
        return databaseClient.sql("""
                        SELECT * FROM diagnosis_tasks
                        WHERE status IN ('PENDING', 'RUNNING') AND updated_at < :cutoff
                        ORDER BY updated_at ASC
                        """)
                .bind("cutoff", toDatabaseTime(cutoff))
                .map((row, metadata) -> mapTask(row)).all();
    }

    private DiagnosisTask mapTask(Row row) {
        String failureReason = row.get("failure_reason", String.class);
        return DiagnosisTask.restore(row.get("id", String.class), row.get("incident_id", String.class),
                number(row.get("version", Integer.class)),
                DiagnosisTaskStatus.valueOf(row.get("status", String.class)),
                toInstant(row, "created_at"), toInstant(row, "updated_at"),
                failureReason == null || failureReason.isBlank() ? null : failureReason);
    }

    private DiagnosisReport mapReport(Row row) {
        try {
            return DiagnosisReport.restore(row.get("id", String.class), row.get("task_id", String.class),
                    number(row.get("version", Integer.class)), row.get("root_cause", String.class),
                    decimal(row.get("confidence", Double.class)),
                    objectMapper.readValue(row.get("evidence", String.class), STRING_LIST),
                    objectMapper.readValue(row.get("recommendations", String.class), STRING_LIST),
                    objectMapper.readValue(row.get("tool_calls", String.class), STRING_LIST),
                    toInstant(row, "generated_at"));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid diagnosis report JSON", exception);
        }
    }

    private int number(Integer value) {
        return value == null ? 0 : value;
    }

    private double decimal(Double value) {
        return value == null ? 0 : value;
    }

    private LocalDateTime toDatabaseTime(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private Instant toInstant(Row row, String column) {
        LocalDateTime value = row.get(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
