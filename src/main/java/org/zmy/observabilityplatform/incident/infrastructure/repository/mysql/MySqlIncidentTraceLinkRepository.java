package org.zmy.observabilityplatform.incident.infrastructure.repository.mysql;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.zmy.observabilityplatform.incident.domain.model.IncidentTraceLink;
import org.zmy.observabilityplatform.incident.domain.repository.IncidentTraceLinkRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Repository
@ConditionalOnProperty(name = "app.adapters.mode", havingValue = "external")
public class MySqlIncidentTraceLinkRepository implements IncidentTraceLinkRepository {
    private final DatabaseClient databaseClient;

    public MySqlIncidentTraceLinkRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Void> link(IncidentTraceLink link) {
        return databaseClient.sql("""
                        INSERT IGNORE INTO incident_trace_links (incident_id, trace_id, linked_at)
                        VALUES (:incidentId, :traceId, :linkedAt)
                        """)
                .bind("incidentId", link.getIncidentId())
                .bind("traceId", link.getTraceId())
                .bind("linkedAt", LocalDateTime.ofInstant(link.getLinkedAt(), ZoneOffset.UTC))
                .fetch().rowsUpdated().then();
    }

    @Override
    public Flux<String> findTraceIdsByIncidentId(String incidentId, int limit) {
        return databaseClient.sql("""
                        SELECT trace_id FROM incident_trace_links
                        WHERE incident_id = :incidentId ORDER BY linked_at DESC LIMIT :limit
                        """)
                .bind("incidentId", incidentId).bind("limit", limit)
                .map((row, metadata) -> row.get("trace_id", String.class)).all();
    }

    @Override
    public Flux<String> findIncidentIdsByTraceId(String traceId, int limit) {
        return databaseClient.sql("""
                        SELECT incident_id FROM incident_trace_links
                        WHERE trace_id = :traceId ORDER BY linked_at DESC LIMIT :limit
                        """)
                .bind("traceId", traceId).bind("limit", limit)
                .map((row, metadata) -> row.get("incident_id", String.class)).all();
    }
}
