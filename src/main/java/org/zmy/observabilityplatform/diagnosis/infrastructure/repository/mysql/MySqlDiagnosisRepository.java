package org.zmy.observabilityplatform.diagnosis.infrastructure.repository.mysql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.Row;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisReport;
import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisTask;
import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisTaskStatus;
import org.zmy.observabilityplatform.diagnosis.domain.repository.DiagnosisRepository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class MySqlDiagnosisRepository implements DiagnosisRepository {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private final DatabaseClient databaseClient;
    private final ObjectMapper objectMapper;

    public MySqlDiagnosisRepository(DatabaseClient databaseClient, ObjectMapper objectMapper) {
        this.databaseClient = databaseClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<DiagnosisTask> saveTask(DiagnosisTask task) {
        return databaseClient.sql("""
                        INSERT INTO diagnosis_tasks (id, incident_id, version, status, created_at, updated_at, failure_reason)
                        VALUES (:id, :incidentId, :version, :status, :createdAt, :updatedAt, :failureReason)
                        AS new
                        ON DUPLICATE KEY UPDATE status = new.status, updated_at = new.updated_at,
                            failure_reason = new.failure_reason
                        """)
                .bind("id", task.getId()).bind("incidentId", task.getIncidentId()).bind("version", task.getVersion())
                .bind("status", task.getStatus().name()).bind("createdAt", toDatabaseTime(task.getCreatedAt()))
                .bind("updatedAt", toDatabaseTime(task.getUpdatedAt()))
                .bind("failureReason", task.getFailureReason() == null ? "" : task.getFailureReason())
                .fetch().rowsUpdated()
                .then(findTaskById(task.getId()));
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
    public Mono<DiagnosisReport> saveReport(DiagnosisReport report) {
        try {
            return databaseClient.sql("""
                            INSERT INTO diagnosis_reports
                                (id, task_id, version, root_cause, confidence, evidence, recommendations, tool_calls, generated_at)
                            VALUES (:id, :taskId, :version, :rootCause, :confidence, :evidence, :recommendations, :toolCalls, :generatedAt)
                            AS new
                            ON DUPLICATE KEY UPDATE root_cause = new.root_cause,
                                confidence = new.confidence, evidence = new.evidence,
                                recommendations = new.recommendations, tool_calls = new.tool_calls,
                                generated_at = new.generated_at
                            """)
                    .bind("id", report.getId()).bind("taskId", report.getTaskId()).bind("version", report.getVersion())
                    .bind("rootCause", report.getRootCause()).bind("confidence", report.getConfidence())
                    .bind("evidence", objectMapper.writeValueAsString(report.getEvidence()))
                    .bind("recommendations", objectMapper.writeValueAsString(report.getRecommendations()))
                    .bind("toolCalls", objectMapper.writeValueAsString(report.getToolCalls()))
                    .bind("generatedAt", toDatabaseTime(report.getGeneratedAt()))
                    .fetch().rowsUpdated()
                    .then(findReport(report.getTaskId(), report.getVersion()));
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }
    }

    @Override
    public Mono<DiagnosisReport> findReportByTaskId(String taskId) {
        return databaseClient.sql("SELECT * FROM diagnosis_reports WHERE task_id = :taskId ORDER BY version DESC LIMIT 1")
                .bind("taskId", taskId).map((row, metadata) -> mapReport(row)).one();
    }

    private Mono<DiagnosisReport> findReport(String taskId, int version) {
        return databaseClient.sql("SELECT * FROM diagnosis_reports WHERE task_id = :taskId AND version = :version")
                .bind("taskId", taskId).bind("version", version)
                .map((row, metadata) -> mapReport(row)).one();
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
