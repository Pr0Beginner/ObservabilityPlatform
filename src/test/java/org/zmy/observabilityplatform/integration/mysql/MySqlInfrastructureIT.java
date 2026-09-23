package org.zmy.observabilityplatform.integration.mysql;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.ConnectionFactories;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.zmy.observabilityplatform.audit.application.query.AuditSearchQuery;
import org.zmy.observabilityplatform.audit.domain.model.AuditAction;
import org.zmy.observabilityplatform.audit.domain.model.AuditActor;
import org.zmy.observabilityplatform.audit.domain.model.AuditOutcome;
import org.zmy.observabilityplatform.audit.domain.model.AuditRecord;
import org.zmy.observabilityplatform.audit.domain.model.AuditTargetType;
import org.zmy.observabilityplatform.audit.infrastructure.repository.mysql.MySqlAuditRepository;
import org.zmy.observabilityplatform.diagnosis.domain.event.DiagnosisRequestedEvent;
import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisReport;
import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisTask;
import org.zmy.observabilityplatform.diagnosis.domain.model.DiagnosisTaskStatus;
import org.zmy.observabilityplatform.diagnosis.infrastructure.repository.mysql.MySqlDiagnosisRepository;
import org.zmy.observabilityplatform.incident.application.notification.IncidentNotifier;
import org.zmy.observabilityplatform.incident.application.query.IncidentSearchQuery;
import org.zmy.observabilityplatform.incident.application.service.AnomalyEvaluationService;
import org.zmy.observabilityplatform.incident.application.service.AnomalyPolicyResolver;
import org.zmy.observabilityplatform.incident.application.service.IncidentLifecycleService;
import org.zmy.observabilityplatform.incident.application.service.IncidentNotificationService;
import org.zmy.observabilityplatform.incident.domain.exception.IncidentVersionConflictException;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyDetectionSettings;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicy;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicyReference;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicyScope;
import org.zmy.observabilityplatform.incident.domain.model.Incident;
import org.zmy.observabilityplatform.incident.domain.model.IncidentNotification;
import org.zmy.observabilityplatform.incident.domain.model.IncidentStatus;
import org.zmy.observabilityplatform.incident.domain.model.IncidentType;
import org.zmy.observabilityplatform.incident.domain.model.MetricKey;
import org.zmy.observabilityplatform.incident.domain.model.NotificationStatus;
import org.zmy.observabilityplatform.incident.domain.model.NotificationType;
import org.zmy.observabilityplatform.incident.domain.repository.IncidentNotificationRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.mysql.MySqlAnomalyPolicyRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.mysql.MySqlIncidentNotificationRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.mysql.MySqlIncidentRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.mysql.MySqlIncidentTraceLinkRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.mysql.MySqlMetricTraceSampleRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.mysql.MySqlMetricWindowRepository;
import org.zmy.observabilityplatform.shared.messaging.application.query.DeadLetterSearchQuery;
import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterMessage;
import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterReplayAttempt;
import org.zmy.observabilityplatform.shared.messaging.domain.model.DeadLetterStatus;
import org.zmy.observabilityplatform.shared.messaging.domain.model.ReplayAttemptStatus;
import org.zmy.observabilityplatform.shared.messaging.infrastructure.repository.mysql.MySqlDeadLetterReplayAttemptRepository;
import org.zmy.observabilityplatform.shared.messaging.infrastructure.repository.mysql.MySqlDeadLetterRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@Testcontainers
class MySqlInfrastructureIT {
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC);

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("observability")
            .withUsername("observability")
            .withPassword("observability");

    private static DatabaseClient databaseClient;
    private static MySqlMetricWindowRepository metricRepository;
    private static MySqlIncidentRepository incidentRepository;
    private static IncidentNotificationRepository notificationRepository;
    private static MySqlAnomalyPolicyRepository anomalyPolicyRepository;
    private static MySqlMetricTraceSampleRepository traceSampleRepository;
    private static MySqlDeadLetterRepository deadLetterRepository;
    private static MySqlDeadLetterReplayAttemptRepository replayAttemptRepository;
    private static MySqlAuditRepository auditRepository;
    private static IncidentLifecycleService lifecycleService;
    private static AnomalyEvaluationService anomalyService;

    @BeforeAll
    static void migrateAndCreateRepositories() {
        migrate(MYSQL.getJdbcUrl(), null);
        String r2dbcUrl = "r2dbc:mysql://" + MYSQL.getUsername() + ":" + MYSQL.getPassword()
                + "@" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/" + MYSQL.getDatabaseName() + "?serverZoneId=UTC";
        databaseClient = DatabaseClient.create(ConnectionFactories.get(r2dbcUrl));
        metricRepository = new MySqlMetricWindowRepository(databaseClient);
        incidentRepository = new MySqlIncidentRepository(databaseClient);
        notificationRepository = new MySqlIncidentNotificationRepository(databaseClient);
        anomalyPolicyRepository = new MySqlAnomalyPolicyRepository(databaseClient);
        traceSampleRepository = new MySqlMetricTraceSampleRepository(databaseClient);
        deadLetterRepository = new MySqlDeadLetterRepository(databaseClient);
        replayAttemptRepository = new MySqlDeadLetterReplayAttemptRepository(databaseClient);
        auditRepository = new MySqlAuditRepository(databaseClient, new ObjectMapper());
        MySqlIncidentTraceLinkRepository traceLinkRepository =
                new MySqlIncidentTraceLinkRepository(databaseClient);
        IncidentNotifier notifier = (incident, notification) -> Mono.empty();
        IncidentNotificationService notificationService = new IncidentNotificationService(
                notificationRepository, incidentRepository, notifier, CLOCK, 120, 5);
        lifecycleService = new IncidentLifecycleService(
                incidentRepository, notificationService);
        AnomalyPolicyResolver policyResolver = new AnomalyPolicyResolver(anomalyPolicyRepository);
        anomalyService = new AnomalyEvaluationService(metricRepository, incidentRepository, lifecycleService,
                traceSampleRepository, traceLinkRepository, anomalyPolicyRepository, policyResolver, CLOCK);
    }

    @BeforeEach
    void clearBusinessData() {
        execute("DELETE FROM diagnosis_reports")
                .then(execute("DELETE FROM diagnosis_tasks"))
                .then(execute("DELETE FROM incident_notifications"))
                .then(execute("DELETE FROM incident_trace_links"))
                .then(execute("DELETE FROM audit_records"))
                .then(execute("DELETE FROM metric_trace_samples"))
                .then(execute("DELETE FROM metric_observations"))
                .then(execute("DELETE FROM metric_windows"))
                .then(execute("DELETE FROM dead_letter_messages"))
                .then(execute("DELETE FROM incidents"))
                .then(execute("DELETE FROM anomaly_policies WHERE id <> 'global-default'"))
                .block(TIMEOUT);
    }

    @Test
    void migratesFreshDatabaseToLatestVersion() {
        List<String> versions = databaseClient.sql("""
                        SELECT version FROM flyway_schema_history
                        WHERE success = 1 AND type = 'SQL' ORDER BY installed_rank
                        """)
                .map((row, metadata) -> row.get("version", String.class))
                .all().collectList().block(TIMEOUT);

        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8");

        List<String> retentionIndexes = databaseClient.sql("""
                        SELECT index_name FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND index_name IN ('idx_metric_trace_samples_start', 'idx_dead_letter_replayed_at')
                        ORDER BY index_name
                        """)
                .map((row, metadata) -> row.get("index_name", String.class))
                .all().collectList().block(TIMEOUT);
        assertThat(retentionIndexes).containsExactly(
                "idx_dead_letter_replayed_at", "idx_metric_trace_samples_start");

        Long replayAttemptTable = databaseClient.sql("""
                        SELECT COUNT(*) AS total FROM information_schema.tables
                        WHERE table_schema = DATABASE() AND table_name = 'dead_letter_replay_attempts'
                        """)
                .map((row, metadata) -> row.get("total", Long.class)).one().block(TIMEOUT);
        assertThat(replayAttemptTable).isEqualTo(1L);

        Long auditTable = databaseClient.sql("""
                        SELECT COUNT(*) AS total FROM information_schema.tables
                        WHERE table_schema = DATABASE() AND table_name = 'audit_records'
                        """)
                .map((row, metadata) -> row.get("total", Long.class)).one().block(TIMEOUT);
        assertThat(auditTable).isEqualTo(1L);
    }

    @Test
    void upgradesVersionOneDatabaseAndBackfillsLegacyIncident() throws Exception {
        String database = "observability_upgrade";
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + database
                    + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        String upgradeUrl = jdbcUrl(database);
        migrate(upgradeUrl, "1");
        try (Connection connection = DriverManager.getConnection(
                upgradeUrl, MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            // 模拟早期由 schema.sql 创建、尚未纳入 Flyway 管理的 V1 数据库。
            statement.execute("DROP TABLE flyway_schema_history");
            statement.executeUpdate("""
                    INSERT INTO incidents
                        (id, dedup_key, title, service, environment, fingerprint, severity, status,
                         started_at, updated_at, error_count, assignee, resolution)
                    VALUES
                        ('legacy-incident', 'legacy-dedup', 'Legacy incident', 'orders', 'prod',
                         'legacy-fingerprint', 'P2', 'OPEN', '2026-09-19 10:00:00.000000',
                         '2026-09-19 10:05:00.000000', 7, 'unassigned', '')
                    """);
        }

        migrate(upgradeUrl, null);

        try (Connection connection = DriverManager.getConnection(
                upgradeUrl, MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT incident_type, dimension_value, current_value, last_observed_window,
                            policy_id, policy_version, version
                     FROM incidents WHERE id = 'legacy-incident'
                     """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("incident_type")).isEqualTo("REPEATED_ERROR");
            assertThat(result.getString("dimension_value")).isEqualTo("legacy-fingerprint");
            assertThat(result.getDouble("current_value")).isEqualTo(7.0);
            assertThat(result.getTimestamp("last_observed_window")).isNotNull();
            assertThat(result.getString("policy_id")).isEqualTo("global-default");
            assertThat(result.getLong("policy_version")).isEqualTo(1);
            assertThat(result.getLong("version")).isEqualTo(1);
        }
    }

    @Test
    void atomicallyAccumulatesMetricsAcrossConcurrentConnections() {
        MetricKey key = MetricKey.requestTotal("orders", "prod", "GET /orders");
        Instant window = Instant.parse("2026-09-20T10:00:00Z");

        Flux.range(0, 50)
                .flatMap(index -> metricRepository.increment(key, window, 1), 10)
                .then().block(TIMEOUT);

        assertThat(metricRepository.find(key, window).block(TIMEOUT).getCount()).isEqualTo(50);
    }

    @Test
    void persistsAndSearchesStructuredAuditRecords() {
        AuditRecord record = AuditRecord.create("audit-1",
                new AuditActor("operator", List.of("OPERATOR")),
                AuditAction.INCIDENT_ASSIGN, AuditTargetType.INCIDENT, "incident-1",
                AuditOutcome.SUCCEEDED, "trace-1", CLOCK.instant(),
                Map.of("assignee", "unassigned"), Map.of("assignee", "alice"), null);

        auditRepository.save(record).block(TIMEOUT);
        var result = auditRepository.search(new AuditSearchQuery("operator",
                AuditAction.INCIDENT_ASSIGN, AuditTargetType.INCIDENT, "incident-1",
                AuditOutcome.SUCCEEDED, CLOCK.instant().minusSeconds(1),
                CLOCK.instant().plusSeconds(1), 0, 10)).block(TIMEOUT);

        assertThat(result).isNotNull();
        assertThat(result.getItems()).containsExactly(record);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(auditRepository.deleteBefore(CLOCK.instant().plusSeconds(1), 10).block(TIMEOUT))
                .isEqualTo(1);
    }

    @Test
    void persistsAndVersionsServiceSpecificAnomalyPolicy() {
        Instant now = Instant.parse("2026-09-20T10:00:00Z");
        AnomalyPolicy created = anomalyPolicyRepository.create(AnomalyPolicy.create(
                "orders-policy", "Orders production", AnomalyPolicyScope.SERVICE,
                "orders", "prod", null, true, AnomalyDetectionSettings.defaults(), now))
                .block(TIMEOUT);

        AnomalyPolicy found = anomalyPolicyRepository.findByScope(
                AnomalyPolicyScope.SERVICE, "orders", "prod", null).block(TIMEOUT);
        AnomalyPolicy updated = anomalyPolicyRepository.update(found.revise(
                "Orders production disabled", found.getScope(), found.getService(), found.getEnvironment(),
                null, false, found.getSettings(), now.plusSeconds(1))).block(TIMEOUT);

        assertThat(found).isEqualTo(created);
        assertThat(updated.getVersion()).isEqualTo(2);
        assertThat(updated.isEnabled()).isFalse();
    }

    @Test
    void persistsAnomalyAndAutomaticallyRecoversAfterHealthyWindows() {
        MetricKey total = MetricKey.requestTotal("orders", "prod", "GET /orders");
        Instant anomalousWindow = Instant.parse("2026-09-20T10:00:00Z");
        recordTotal(total, anomalousWindow.minus(Duration.ofDays(1)), 100);
        recordTotal(total, anomalousWindow, 250);

        anomalyService.evaluate(anomalousWindow).block(TIMEOUT);

        Incident opened = findIncident(IncidentType.REQUEST_VOLUME_SPIKE);
        assertThat(opened.getStatus()).isEqualTo(IncidentStatus.OPEN);

        Instant firstHealthyWindow = anomalousWindow.plus(Duration.ofMinutes(5));
        recordTotal(total, firstHealthyWindow.minus(Duration.ofDays(1)), 100);
        recordTotal(total, firstHealthyWindow, 100);
        anomalyService.evaluate(firstHealthyWindow).block(TIMEOUT);

        Instant secondHealthyWindow = firstHealthyWindow.plus(Duration.ofMinutes(5));
        recordTotal(total, secondHealthyWindow.minus(Duration.ofDays(1)), 100);
        recordTotal(total, secondHealthyWindow, 100);
        anomalyService.evaluate(secondHealthyWindow).block(TIMEOUT);

        Incident recovered = incidentRepository.findById(opened.getId()).block(TIMEOUT);
        assertThat(recovered.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(recovered.getRecoveredAt()).isNotNull();
        assertThat(notificationRepository.findByIncidentId(opened.getId(), 10)
                .collectList().block(TIMEOUT)).hasSize(2);
    }

    @Test
    void rejectsAnUpdateBasedOnAStaleIncidentVersion() {
        Instant now = Instant.parse("2026-09-20T10:00:00Z");
        Incident original = incidentRepository.save(Incident.open(
                "incident-1", "dedup-1", "orders", "prod", "fp-1", "ERROR", 3, now, now,
                new AnomalyPolicyReference("global-default", 1)))
                .block(TIMEOUT);
        Incident assigned = incidentRepository.save(original.assignTo("on-call", now.plusSeconds(1)))
                .block(TIMEOUT);

        assertThatThrownBy(() -> incidentRepository.save(original.transitionTo(
                        IncidentStatus.TRIAGING, null, now.plusSeconds(2))).block(TIMEOUT))
                .isInstanceOf(IncidentVersionConflictException.class);
        assertThat(incidentRepository.findById(original.getId()).block(TIMEOUT))
                .isEqualTo(assigned);
    }

    @Test
    void allowsOnlyOneInstanceToClaimANotification() {
        Instant now = Instant.parse("2026-09-20T10:00:00Z");
        Incident incident = incidentRepository.save(Incident.open(
                "incident-1", "dedup-1", "orders", "prod", "fp-1", "ERROR", 3, now, now,
                new AnomalyPolicyReference("global-default", 1)))
                .block(TIMEOUT);
        IncidentNotification pending = IncidentNotification.pending(
                "notification-1", "notification-key", incident.getId(), NotificationType.OPENED, now);
        notificationRepository.createIfAbsent(pending).block(TIMEOUT);

        List<IncidentNotification> claims = Flux.merge(
                        notificationRepository.claim(pending.getNotificationKey(), "worker-1", now,
                                now.plusSeconds(120)),
                        notificationRepository.claim(pending.getNotificationKey(), "worker-2", now,
                                now.plusSeconds(120)))
                .collectList().block(TIMEOUT);

        assertThat(claims).hasSize(1);
        assertThat(claims.get(0).getStatus()).isEqualTo(NotificationStatus.SENDING);
        assertThat(claims.get(0).getAttempts()).isEqualTo(1);
    }

    @Test
    void deletesExpiredOperationalDataInBoundedBatches() {
        Instant now = CLOCK.instant();
        Instant firstExpired = now.minus(Duration.ofDays(10));
        Instant secondExpired = now.minus(Duration.ofDays(9));
        Instant recent = now.minus(Duration.ofHours(1));
        Instant cutoff = now.minus(Duration.ofDays(7));
        MetricKey key = MetricKey.requestTotal("orders", "prod", "GET /orders");
        for (Instant window : List.of(firstExpired, secondExpired, recent)) {
            metricRepository.increment(key, window, 1).block(TIMEOUT);
            traceSampleRepository.recordIfAbsent(key, window, "trace-" + window).block(TIMEOUT);
        }

        assertThat(traceSampleRepository.deleteBefore(cutoff, 1).block(TIMEOUT)).isEqualTo(1);
        assertThat(traceSampleRepository.deleteBefore(cutoff, 10).block(TIMEOUT)).isEqualTo(1);
        assertThat(metricRepository.deleteBefore(cutoff, 1).block(TIMEOUT)).isEqualTo(1);
        assertThat(metricRepository.deleteBefore(cutoff, 10).block(TIMEOUT)).isEqualTo(1);
        assertThat(metricRepository.find(key, recent).block(TIMEOUT)).isNotNull();
        assertThat(traceSampleRepository.findTraceId(key, recent).block(TIMEOUT)).isNotNull();

        Incident incident = incidentRepository.save(Incident.open(
                "retention-incident", "retention-dedup", "orders", "prod", "fp-retention",
                "ERROR", 3, firstExpired, firstExpired,
                new AnomalyPolicyReference("global-default", 1))).block(TIMEOUT);
        IncidentNotification sent = IncidentNotification.pending(
                        "sent-notification", "sent-key", incident.getId(), NotificationType.OPENED, firstExpired)
                .claim("worker", firstExpired.plusSeconds(120), firstExpired.plusSeconds(1))
                .sent(firstExpired.plusSeconds(2));
        IncidentNotification failed = IncidentNotification.pending(
                        "failed-notification", "failed-key", incident.getId(), NotificationType.OPENED, firstExpired)
                .claim("worker", firstExpired.plusSeconds(120), firstExpired.plusSeconds(1))
                .deliveryFailed("temporary", 5, firstExpired.plusSeconds(2));
        notificationRepository.createIfAbsent(sent).block(TIMEOUT);
        notificationRepository.createIfAbsent(failed).block(TIMEOUT);

        assertThat(notificationRepository.deleteTerminalBefore(cutoff, 10).block(TIMEOUT)).isEqualTo(1);
        assertThat(notificationRepository.findByIncidentId(incident.getId(), 10).collectList().block(TIMEOUT))
                .extracting(IncidentNotification::getNotificationKey)
                .containsExactly("failed-key");

        DeadLetterMessage replayed = DeadLetterMessage.captured(
                "logs.raw.v1", "replayed", "{}", "invalid", 0, 1, firstExpired);
        deadLetterRepository.saveIfAbsent(replayed).block(TIMEOUT);
        deadLetterRepository.save(replayed.replayed(secondExpired)).block(TIMEOUT);
        DeadLetterMessage unresolved = DeadLetterMessage.captured(
                "logs.raw.v1", "unresolved", "{}", "invalid", 0, 2, firstExpired);
        deadLetterRepository.saveIfAbsent(unresolved).block(TIMEOUT);

        assertThat(deadLetterRepository.deleteReplayedBefore(cutoff, 10).block(TIMEOUT)).isEqualTo(1);
        assertThat(deadLetterRepository.deleteUnreplayedBefore(cutoff, now, 10).block(TIMEOUT)).isEqualTo(1);
    }

    @Test
    void searchesOperationsAndProtectsDeadLetterReplayWithALease() {
        Instant now = CLOCK.instant();
        Incident orders = incidentRepository.save(Incident.open(
                "query-orders", "query-orders-dedup", "orders", "prod", "fp-orders",
                "ERROR", 3, now.minusSeconds(60), now.minusSeconds(60),
                new AnomalyPolicyReference("global-default", 1))).block(TIMEOUT);
        incidentRepository.save(Incident.open(
                "query-payments", "query-payments-dedup", "payments", "prod", "fp-payments",
                "ERROR", 3, now.minusSeconds(30), now.minusSeconds(30),
                new AnomalyPolicyReference("global-default", 1))).block(TIMEOUT);

        var incidentPage = incidentRepository.search(new IncidentSearchQuery(
                IncidentStatus.OPEN, IncidentType.REPEATED_ERROR, "orders", "prod", "unassigned",
                now.minus(Duration.ofHours(1)), now, 0, 10)).block(TIMEOUT);
        assertThat(incidentPage).isNotNull();
        assertThat(incidentPage.getItems()).containsExactly(orders);
        assertThat(incidentPage.getTotalElements()).isEqualTo(1);

        DeadLetterMessage message = DeadLetterMessage.captured(
                "logs.raw.v1", "query-message", "{}", "java.lang.IllegalArgumentException",
                "invalid payload", 0, 99, now.minusSeconds(10));
        deadLetterRepository.saveIfAbsent(message).block(TIMEOUT);
        assertThat(deadLetterRepository.claimForReplay(message.getId(), "worker-1", now,
                now.plusSeconds(30)).block(TIMEOUT)).isEqualTo(message);
        assertThat(deadLetterRepository.claimForReplay(message.getId(), "worker-2", now,
                now.plusSeconds(30)).block(TIMEOUT)).isNull();

        DeadLetterReplayAttempt attempt = DeadLetterReplayAttempt.start(
                "replay-attempt-1", message.getId(), now);
        replayAttemptRepository.save(attempt).block(TIMEOUT);
        DeadLetterMessage replayed = deadLetterRepository.completeReplay(
                message.getId(), "worker-1", now.plusSeconds(1)).block(TIMEOUT);
        replayAttemptRepository.save(attempt.succeed(now.plusSeconds(1))).block(TIMEOUT);
        assertThat(replayed).isNotNull();

        var deadLetterPage = deadLetterRepository.search(new DeadLetterSearchQuery(
                "logs.raw.v1", DeadLetterStatus.REPLAYED, "java.lang.IllegalArgumentException",
                now.minus(Duration.ofHours(1)), now, 0, 10)).block(TIMEOUT);
        assertThat(deadLetterPage).isNotNull();
        assertThat(deadLetterPage.getItems()).containsExactly(replayed);
        assertThat(replayAttemptRepository.findByDeadLetterId(message.getId(), 10)
                .single().block(TIMEOUT).getStatus()).isEqualTo(ReplayAttemptStatus.SUCCEEDED);
    }

    @Test
    void concurrentObservationRedeliveryIncrementsCountersOnce() {
        var keys = List.of(MetricKey.requestTotal("orders", "test", "route"),
                MetricKey.requestFailure("orders", "test", "route"));
        Flux.range(0, 8).flatMap(index -> metricRepository.recordOnce("same-observation", keys, CLOCK.instant()))
                .blockLast(TIMEOUT);
        keys.forEach(key -> assertThat(metricRepository.find(key, CLOCK.instant()).block(TIMEOUT).getCount()).isEqualTo(1));
    }

    @Test
    void observationFailureRollsBackBothCountersAndReceipt() {
        var failing = new MySqlMetricWindowRepository(databaseClient) {
            @Override
            public Mono<Void> incrementAll(List<MetricKey> keys, Instant window) {
                return super.incrementAll(keys, window).then(Mono.error(new IllegalStateException("after increment")));
            }
        };
        var key = MetricKey.requestTotal("orders", "test", "route");
        assertThatThrownBy(() -> failing.recordOnce("rollback-observation", List.of(key), CLOCK.instant()).block(TIMEOUT))
                .isInstanceOf(IllegalStateException.class);
        assertThat(metricRepository.find(key, CLOCK.instant()).block(TIMEOUT)).isNull();
        metricRepository.recordOnce("rollback-observation", List.of(key), CLOCK.instant()).block(TIMEOUT);
        assertThat(metricRepository.find(key, CLOCK.instant()).block(TIMEOUT).getCount()).isEqualTo(1);
    }

    @Test
    void requestInsertFailureRollsBackNewTask() {
        var diagnoses = new MySqlDiagnosisRepository(databaseClient, new ObjectMapper());
        var first = newDiagnosisTask("first");
        var second = newDiagnosisTask("second");
        diagnoses.createTask(first, request(first, "duplicate-event")).block(TIMEOUT);
        assertThatThrownBy(() -> diagnoses.createTask(second, request(second, "duplicate-event")).block(TIMEOUT))
                .isInstanceOf(RuntimeException.class);
        assertThat(diagnoses.findTaskById(second.getId()).block(TIMEOUT)).isNull();
        assertThat(diagnoses.pending(10).count().block(TIMEOUT)).isEqualTo(1);
    }

    @Test
    void reportInsertFailureRollsBackTheSuccessfulStateTransition() {
        var diagnoses = new MySqlDiagnosisRepository(databaseClient, new ObjectMapper());
        var first = newDiagnosisTask("first");
        var second = newDiagnosisTask("second");
        diagnoses.createTask(first, request(first, "event-first")).block(TIMEOUT);
        diagnoses.createTask(second, request(second, "event-second")).block(TIMEOUT);
        var firstReport = report(first, "shared-report-id");
        diagnoses.complete(first, first.complete(firstReport, CLOCK.instant()), firstReport).block(TIMEOUT);
        var conflictingReport = report(second, "shared-report-id");
        assertThatThrownBy(() -> diagnoses.complete(second, second.complete(conflictingReport, CLOCK.instant()),
                conflictingReport).block(TIMEOUT)).isInstanceOf(RuntimeException.class);
        assertThat(diagnoses.findTaskById(second.getId()).block(TIMEOUT).getStatus()).isEqualTo(DiagnosisTaskStatus.PENDING);
        assertThat(diagnoses.findReportByTaskId(second.getId()).block(TIMEOUT)).isNull();
        assertThat(diagnoses.pending(10).count().block(TIMEOUT)).isEqualTo(1);
    }

    @Test
    void cancellationAndCompletionHaveExactlyOneWinner() {
        var diagnoses = new MySqlDiagnosisRepository(databaseClient, new ObjectMapper());
        var task = newDiagnosisTask("race");
        diagnoses.createTask(task, request(task, "race-event")).block(TIMEOUT);
        var report = report(task, "race-report");
        var results = Flux.merge(
                diagnoses.transition(task, task.cancel(CLOCK.instant())).materialize(),
                diagnoses.complete(task, task.complete(report, CLOCK.instant()), report).materialize())
                .collectList().block(TIMEOUT);
        assertThat(results.stream().filter(signal -> signal.isOnNext()).count()).isEqualTo(1);
        assertThat(results.stream().filter(signal -> signal.isOnError()).count()).isEqualTo(1);
        var stored = diagnoses.findTaskById(task.getId()).block(TIMEOUT);
        assertThat(stored.getStatus()).isIn(DiagnosisTaskStatus.SUCCEEDED, DiagnosisTaskStatus.CANCELLED);
        assertThat(diagnoses.findReportByTaskId(task.getId()).block(TIMEOUT) != null)
                .isEqualTo(stored.getStatus() == DiagnosisTaskStatus.SUCCEEDED);
    }

    private DiagnosisTask newDiagnosisTask(String id) {
        String incidentId = "incident-" + id;
        incidentRepository.save(Incident.open(incidentId, "dedup-" + id, "orders", "test", "fp-" + id,
                "ERROR", 3, CLOCK.instant(), CLOCK.instant(), new AnomalyPolicyReference("global-default", 1)))
                .block(TIMEOUT);
        return DiagnosisTask.request("task-" + id, incidentId, 1, CLOCK.instant());
    }

    private DiagnosisRequestedEvent request(DiagnosisTask task, String id) {
        return new DiagnosisRequestedEvent(id, task.getId(), task.getIncidentId(), task.getVersion(), task.getCreatedAt());
    }

    private DiagnosisReport report(DiagnosisTask task, String id) {
        return DiagnosisReport.generate(id, task.getId(), task.getVersion(), "database timeout", 0.8,
                List.of("evidence"), List.of("inspect"), List.of("context"), CLOCK.instant());
    }

    private static Flyway migrate(String jdbcUrl, String target) {
        var configuration = Flyway.configure()
                .dataSource(jdbcUrl, MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .validateMigrationNaming(true)
                .cleanDisabled(true);
        if (target != null) {
            configuration.target(target);
        }
        Flyway flyway = configuration.load();
        flyway.migrate();
        return flyway;
    }

    private static String jdbcUrl(String database) {
        return "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    private Mono<Void> execute(String sql) {
        return databaseClient.sql(sql).fetch().rowsUpdated().then();
    }

    private void recordTotal(MetricKey key, Instant window, long count) {
        metricRepository.increment(key, window, count).block(TIMEOUT);
    }

    private Incident findIncident(IncidentType type) {
        return incidentRepository.findAll()
                .filter(incident -> incident.getType() == type)
                .single().block(TIMEOUT);
    }
}
