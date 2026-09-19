CREATE TABLE anomaly_policies (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    scope VARCHAR(20) NOT NULL,
    scope_key CHAR(64) NOT NULL UNIQUE,
    service VARCHAR(120) NOT NULL DEFAULT '',
    environment VARCHAR(80) NOT NULL DEFAULT '',
    operation_name VARCHAR(255) NOT NULL DEFAULT '',
    enabled BOOLEAN NOT NULL,
    error_threshold INT NOT NULL,
    minimum_requests INT NOT NULL,
    minimum_error_code_count INT NOT NULL,
    request_spike_ratio DOUBLE NOT NULL,
    request_drop_ratio DOUBLE NOT NULL,
    failure_rate_threshold DOUBLE NOT NULL,
    error_code_rate_threshold DOUBLE NOT NULL,
    baseline_multiplier DOUBLE NOT NULL,
    comparison_period_minutes INT NOT NULL,
    recovery_windows INT NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    INDEX idx_anomaly_policies_scope (scope, service, environment, operation_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO anomaly_policies
    (id, name, scope, scope_key, service, environment, operation_name, enabled,
     error_threshold, minimum_requests, minimum_error_code_count, request_spike_ratio,
     request_drop_ratio, failure_rate_threshold, error_code_rate_threshold, baseline_multiplier,
     comparison_period_minutes, recovery_windows, version, created_at, updated_at)
VALUES
    ('global-default', 'Global default', 'GLOBAL',
     '87301bfc76dfb677e692ddda889be5382aa44fcf1ff2b93cb2da4e8c466d0b6b',
     '', '', '', TRUE, 3, 20, 5, 2.0, 0.5, 0.1, 0.05, 2.0, 1440, 2, 1,
     UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

ALTER TABLE incidents
    ADD COLUMN policy_id VARCHAR(36) NOT NULL DEFAULT 'global-default' AFTER last_observed_window,
    ADD COLUMN policy_version BIGINT NOT NULL DEFAULT 1 AFTER policy_id,
    ADD INDEX idx_incidents_policy (policy_id, policy_version),
    ADD CONSTRAINT fk_incidents_policy FOREIGN KEY (policy_id) REFERENCES anomaly_policies(id);
