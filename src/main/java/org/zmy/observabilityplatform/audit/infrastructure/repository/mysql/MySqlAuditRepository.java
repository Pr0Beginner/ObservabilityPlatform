package org.zmy.observabilityplatform.audit.infrastructure.repository.mysql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.Row;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.audit.application.query.AuditQueryRepository;
import org.zmy.observabilityplatform.audit.application.query.AuditSearchQuery;
import org.zmy.observabilityplatform.audit.domain.model.AuditAction;
import org.zmy.observabilityplatform.audit.domain.model.AuditOutcome;
import org.zmy.observabilityplatform.audit.domain.model.AuditRecord;
import org.zmy.observabilityplatform.audit.domain.model.AuditTargetType;
import org.zmy.observabilityplatform.audit.domain.repository.AuditRepository;
import org.zmy.observabilityplatform.shared.application.query.PageResult;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class MySqlAuditRepository implements AuditRepository, AuditQueryRepository {
    private static final String COLUMNS = "id, actor, roles, action_type, target_type, target_id, outcome, trace_id, "
            + "occurred_at, before_state, after_state, failure_reason";
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() { };
    private final DatabaseClient databaseClient;
    private final ObjectMapper objectMapper;

    public MySqlAuditRepository(DatabaseClient databaseClient, ObjectMapper objectMapper) {
        this.databaseClient = databaseClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<AuditRecord> save(AuditRecord record) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                        INSERT INTO audit_records (id, actor, roles, action_type, target_type, target_id, outcome,
                            trace_id, occurred_at, before_state, after_state, failure_reason)
                        VALUES (:id, :actor, :roles, :action, :targetType, :targetId, :outcome,
                            :traceId, :occurredAt, :beforeState, :afterState, :failureReason)
                        """)
                .bind("id", record.getId())
                .bind("actor", record.getActor())
                .bind("roles", json(record.getRoles()))
                .bind("action", record.getAction().name())
                .bind("targetType", record.getTargetType().name())
                .bind("targetId", record.getTargetId())
                .bind("outcome", record.getOutcome().name())
                .bind("traceId", record.getTraceId())
                .bind("occurredAt", toDatabaseTime(record.getOccurredAt()))
                .bind("beforeState", json(record.getBeforeState()))
                .bind("afterState", json(record.getAfterState()));
        spec = record.getFailureReason() == null
                ? spec.bindNull("failureReason", String.class)
                : spec.bind("failureReason", record.getFailureReason());
        return spec.fetch().rowsUpdated().thenReturn(record);
    }

    @Override
    public Mono<PageResult<AuditRecord>> search(AuditSearchQuery query) {
        String predicate = predicate(query);
        Mono<Long> total = bindFilters(databaseClient.sql(
                        "SELECT COUNT(*) AS total FROM audit_records" + predicate), query)
                .map((row, metadata) -> number(row.get("total"))).one();
        Mono<List<AuditRecord>> items = bindFilters(databaseClient.sql("SELECT " + COLUMNS
                        + " FROM audit_records" + predicate
                        + " ORDER BY occurred_at DESC, id ASC LIMIT :limit OFFSET :offset"), query)
                .bind("limit", query.getSize())
                .bind("offset", query.offset())
                .map((row, metadata) -> map(row))
                .all()
                .collectList();
        return Mono.zip(items, total)
                .map(tuple -> PageResult.of(tuple.getT1(), query.getPage(), query.getSize(), tuple.getT2()));
    }

    @Override
    public Mono<Long> deleteBefore(Instant cutoff, int limit) {
        if (limit < 1) {
            return Mono.error(new IllegalArgumentException("limit must be positive"));
        }
        return databaseClient.sql("""
                        DELETE FROM audit_records
                        WHERE occurred_at < :cutoff
                        ORDER BY occurred_at ASC
                        LIMIT :limit
                        """)
                .bind("cutoff", toDatabaseTime(cutoff))
                .bind("limit", limit)
                .fetch().rowsUpdated();
    }

    private String predicate(AuditSearchQuery query) {
        List<String> conditions = new ArrayList<>();
        add(conditions, query.getActor(), "actor = :actor");
        add(conditions, query.getAction(), "action_type = :action");
        add(conditions, query.getTargetType(), "target_type = :targetType");
        add(conditions, query.getTargetId(), "target_id = :targetId");
        add(conditions, query.getOutcome(), "outcome = :outcome");
        add(conditions, query.getFrom(), "occurred_at >= :fromTime");
        add(conditions, query.getTo(), "occurred_at <= :toTime");
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    private DatabaseClient.GenericExecuteSpec bindFilters(DatabaseClient.GenericExecuteSpec spec,
                                                           AuditSearchQuery query) {
        if (query.getActor() != null) {
            spec = spec.bind("actor", query.getActor());
        }
        if (query.getAction() != null) {
            spec = spec.bind("action", query.getAction().name());
        }
        if (query.getTargetType() != null) {
            spec = spec.bind("targetType", query.getTargetType().name());
        }
        if (query.getTargetId() != null) {
            spec = spec.bind("targetId", query.getTargetId());
        }
        if (query.getOutcome() != null) {
            spec = spec.bind("outcome", query.getOutcome().name());
        }
        if (query.getFrom() != null) {
            spec = spec.bind("fromTime", toDatabaseTime(query.getFrom()));
        }
        if (query.getTo() != null) {
            spec = spec.bind("toTime", toDatabaseTime(query.getTo()));
        }
        return spec;
    }

    private AuditRecord map(Row row) {
        return AuditRecord.restore(row.get("id", String.class), row.get("actor", String.class),
                fromJson(row.get("roles", String.class), STRING_LIST),
                AuditAction.valueOf(row.get("action_type", String.class)),
                AuditTargetType.valueOf(row.get("target_type", String.class)),
                row.get("target_id", String.class),
                AuditOutcome.valueOf(row.get("outcome", String.class)),
                row.get("trace_id", String.class), toInstant(row.get("occurred_at", LocalDateTime.class)),
                fromJson(row.get("before_state", String.class), OBJECT_MAP),
                fromJson(row.get("after_state", String.class), OBJECT_MAP),
                row.get("failure_reason", String.class));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Cannot serialize audit state", exception);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot deserialize audit state", exception);
        }
    }

    private void add(List<String> conditions, Object value, String condition) {
        if (value != null) {
            conditions.add(condition);
        }
    }

    private long number(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private LocalDateTime toDatabaseTime(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private Instant toInstant(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC);
    }
}
