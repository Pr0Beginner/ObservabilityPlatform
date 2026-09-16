package org.zmy.observabilityplatform.diagnosis.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.Row;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.diagnosis.domain.DiagnosisReport;
import org.zmy.observabilityplatform.diagnosis.domain.DiagnosisRepository;
import org.zmy.observabilityplatform.diagnosis.domain.DiagnosisTask;
import org.zmy.observabilityplatform.diagnosis.domain.DiagnosisTaskStatus;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class PostgresDiagnosisRepository implements DiagnosisRepository {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private final DatabaseClient databaseClient;
    private final ObjectMapper objectMapper;

    public PostgresDiagnosisRepository(DatabaseClient databaseClient, ObjectMapper objectMapper) {
        this.databaseClient = databaseClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<DiagnosisTask> saveTask(DiagnosisTask task) {
        return databaseClient.sql("""
                        INSERT INTO diagnosis_tasks (id, incident_id, version, status, created_at, updated_at, failure_reason)
                        VALUES (:id, :incidentId, :version, :status, :createdAt, :updatedAt, :failureReason)
                        ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status, updated_at = EXCLUDED.updated_at,
                            failure_reason = EXCLUDED.failure_reason
                        RETURNING id, incident_id, version, status, created_at, updated_at, failure_reason
                        """)
                .bind("id", task.id()).bind("incidentId", task.incidentId()).bind("version", task.version())
                .bind("status", task.status().name()).bind("createdAt", task.createdAt())
                .bind("updatedAt", task.updatedAt())
                .bind("failureReason", task.failureReason() == null ? "" : task.failureReason())
                .map((row, metadata) -> mapTask(row)).one();
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
                            ON CONFLICT (task_id, version) DO UPDATE SET root_cause = EXCLUDED.root_cause,
                                confidence = EXCLUDED.confidence, evidence = EXCLUDED.evidence,
                                recommendations = EXCLUDED.recommendations, tool_calls = EXCLUDED.tool_calls,
                                generated_at = EXCLUDED.generated_at
                            RETURNING *
                            """)
                    .bind("id", report.id()).bind("taskId", report.taskId()).bind("version", report.version())
                    .bind("rootCause", report.rootCause()).bind("confidence", report.confidence())
                    .bind("evidence", objectMapper.writeValueAsString(report.evidence()))
                    .bind("recommendations", objectMapper.writeValueAsString(report.recommendations()))
                    .bind("toolCalls", objectMapper.writeValueAsString(report.toolCalls()))
                    .bind("generatedAt", report.generatedAt())
                    .map((row, metadata) -> mapReport(row)).one();
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }
    }

    @Override
    public Mono<DiagnosisReport> findReportByTaskId(String taskId) {
        return databaseClient.sql("SELECT * FROM diagnosis_reports WHERE task_id = :taskId ORDER BY version DESC LIMIT 1")
                .bind("taskId", taskId).map((row, metadata) -> mapReport(row)).one();
    }

    private DiagnosisTask mapTask(Row row) {
        String failureReason = row.get("failure_reason", String.class);
        return new DiagnosisTask(row.get("id", String.class), row.get("incident_id", String.class),
                number(row.get("version", Integer.class)),
                DiagnosisTaskStatus.valueOf(row.get("status", String.class)),
                row.get("created_at", Instant.class), row.get("updated_at", Instant.class),
                failureReason == null || failureReason.isBlank() ? null : failureReason);
    }

    private DiagnosisReport mapReport(Row row) {
        try {
            return new DiagnosisReport(row.get("id", String.class), row.get("task_id", String.class),
                    number(row.get("version", Integer.class)), row.get("root_cause", String.class),
                    decimal(row.get("confidence", Double.class)),
                    objectMapper.readValue(row.get("evidence", String.class), STRING_LIST),
                    objectMapper.readValue(row.get("recommendations", String.class), STRING_LIST),
                    objectMapper.readValue(row.get("tool_calls", String.class), STRING_LIST),
                    row.get("generated_at", Instant.class));
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
}
