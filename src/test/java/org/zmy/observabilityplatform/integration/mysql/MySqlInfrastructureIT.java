package org.zmy.observabilityplatform.integration.mysql;

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
import org.zmy.observabilityplatform.incident.application.notification.IncidentNotifier;
import org.zmy.observabilityplatform.incident.application.service.AnomalyEvaluationService;
import org.zmy.observabilityplatform.incident.application.service.AnomalyPolicyResolver;
import org.zmy.observabilityplatform.incident.application.service.IncidentLifecycleService;
import org.zmy.observabilityplatform.incident.application.service.IncidentNotificationService;
import org.zmy.observabilityplatform.incident.domain.exception.IncidentVersionConflictException;
import org.zmy.observabilityplatform.incident.domain.model.Incident;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicyReference;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyDetectionSettings;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicy;
import org.zmy.observabilityplatform.incident.domain.model.AnomalyPolicyScope;
import org.zmy.observabilityplatform.incident.domain.model.IncidentNotification;
import org.zmy.observabilityplatform.incident.domain.model.IncidentStatus;
import org.zmy.observabilityplatform.incident.domain.model.IncidentType;
import org.zmy.observabilityplatform.incident.domain.model.MetricKey;
import org.zmy.observabilityplatform.incident.domain.model.NotificationStatus;
import org.zmy.observabilityplatform.incident.domain.model.NotificationType;
import org.zmy.observabilityplatform.incident.domain.repository.IncidentNotificationRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.mysql.MySqlIncidentNotificationRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.mysql.MySqlAnomalyPolicyRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.mysql.MySqlIncidentRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.mysql.MySqlIncidentTraceLinkRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.mysql.MySqlMetricTraceSampleRepository;
import org.zmy.observabilityplatform.incident.infrastructure.repository.mysql.MySqlMetricWindowRepository;
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
    private static IncidentLifecycleService lifecycleService;
    private static AnomalyEvaluationService anomalyService;

    @BeforeAll
    static void migrateAndCreateRepositories() {
        migrate(MYSQL.getJdbcUrl(), null);
        String r2dbcUrl = "r2dbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/" + MYSQL.getDatabaseName() + "?serverZoneId=UTC";
        databaseClient = DatabaseClient.create(ConnectionFactories.get(r2dbcUrl));
        metricRepository = new MySqlMetricWindowRepository(databaseClient);
        incidentRepository = new MySqlIncidentRepository(databaseClient);
        notificationRepository = new MySqlIncidentNotificationRepository(databaseClient);
        anomalyPolicyRepository = new MySqlAnomalyPolicyRepository(databaseClient);
        MySqlMetricTraceSampleRepository traceSampleRepository =
                new MySqlMetricTraceSampleRepository(databaseClient);
        MySqlIncidentTraceLinkRepository traceLinkRepository =
                new MySqlIncidentTraceLinkRepository(databaseClient);
        IncidentNotifier notifier = (incident, notification) -> Mono.empty();
        IncidentNotificationService notificationService = new IncidentNotificationService(
                notificationRepository, incidentRepository, notifier, CLOCK, 120);
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
                .then(execute("DELETE FROM metric_trace_samples"))
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

        assertThat(versions).containsExactly("1", "2", "3", "4");
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
