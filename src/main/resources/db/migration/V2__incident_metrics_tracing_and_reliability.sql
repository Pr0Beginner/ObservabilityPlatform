ALTER TABLE incidents
    MODIFY COLUMN dedup_key VARCHAR(768) NOT NULL,
    MODIFY COLUMN fingerprint VARCHAR(64) NOT NULL DEFAULT '',
    ADD COLUMN incident_type VARCHAR(40) NOT NULL DEFAULT 'REPEATED_ERROR' AFTER fingerprint,
    ADD COLUMN operation_name VARCHAR(255) NOT NULL DEFAULT '' AFTER incident_type,
    ADD COLUMN dimension_value VARCHAR(255) NOT NULL DEFAULT '' AFTER operation_name,
    ADD COLUMN current_value DOUBLE NULL AFTER resolution,
    ADD COLUMN baseline_value DOUBLE NULL AFTER current_value,
    ADD COLUMN recovered_at TIMESTAMP(6) NULL AFTER baseline_value,
    ADD COLUMN healthy_window_count INT NOT NULL DEFAULT 0 AFTER recovered_at,
    ADD COLUMN last_observed_window TIMESTAMP(6) NULL AFTER healthy_window_count;

UPDATE incidents
SET dimension_value = fingerprint,
    current_value = error_count,
    last_observed_window = started_at
WHERE incident_type = 'REPEATED_ERROR';

CREATE TABLE metric_windows (
    metric_key VARCHAR(64) NOT NULL,
    metric_type VARCHAR(40) NOT NULL,
    service VARCHAR(120) NOT NULL,
    environment VARCHAR(80) NOT NULL,
    operation_name VARCHAR(255) NOT NULL,
    dimension_value VARCHAR(255) NOT NULL,
    window_start TIMESTAMP(6) NOT NULL,
    metric_count BIGINT NOT NULL,
    PRIMARY KEY (metric_key, window_start),
    INDEX idx_metric_windows_start (window_start),
    INDEX idx_metric_windows_scope (service, environment, operation_name, window_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE metric_trace_samples (
    metric_key VARCHAR(64) NOT NULL,
    window_start TIMESTAMP(6) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (metric_key, window_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE incident_notifications (
    id VARCHAR(36) PRIMARY KEY,
    notification_key VARCHAR(255) NOT NULL UNIQUE,
    incident_id VARCHAR(36) NOT NULL,
    notification_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    last_error TEXT NOT NULL,
    INDEX idx_incident_notifications_retry (status, updated_at),
    CONSTRAINT fk_incident_notifications_incident FOREIGN KEY (incident_id) REFERENCES incidents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE incident_trace_links (
    incident_id VARCHAR(36) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    linked_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (incident_id, trace_id),
    INDEX idx_incident_trace_links_trace (trace_id, linked_at DESC),
    CONSTRAINT fk_incident_trace_links_incident FOREIGN KEY (incident_id) REFERENCES incidents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE dead_letter_messages (
    id VARCHAR(64) PRIMARY KEY,
    original_topic VARCHAR(255) NOT NULL,
    message_key VARCHAR(255) NOT NULL,
    payload LONGTEXT NOT NULL,
    failure_reason TEXT NOT NULL,
    source_partition INT NOT NULL,
    source_offset BIGINT NOT NULL,
    failed_at TIMESTAMP(6) NOT NULL,
    replayed_at TIMESTAMP(6) NULL,
    INDEX idx_dead_letter_failed_at (failed_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
