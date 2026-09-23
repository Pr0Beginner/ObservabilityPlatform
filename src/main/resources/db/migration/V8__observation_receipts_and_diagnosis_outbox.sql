-- Retain receipts independently of metric windows: old Kafka/DLQ replays must not recount logs.
CREATE TABLE metric_observations (
    observation_id VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin PRIMARY KEY,
    window_start TIMESTAMP(6) NOT NULL
) ENGINE=InnoDB;

ALTER TABLE diagnosis_tasks
    ADD COLUMN active_incident_id VARCHAR(36)
        GENERATED ALWAYS AS (CASE WHEN status IN ('PENDING', 'RUNNING') THEN incident_id ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_diagnosis_active_incident (active_incident_id);

CREATE TABLE diagnosis_request_outbox (
    event_id VARCHAR(36) PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL,
    incident_id VARCHAR(36) NOT NULL,
    version INT NOT NULL,
    requested_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_diagnosis_request_task (task_id),
    INDEX idx_diagnosis_outbox_requested (requested_at, event_id),
    CONSTRAINT fk_diagnosis_request_task FOREIGN KEY (task_id) REFERENCES diagnosis_tasks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Existing active requests may have been lost before this outbox was introduced.
-- Re-delivery is safe because Agent identifies an execution by task_id + version.
INSERT INTO diagnosis_request_outbox (event_id, task_id, incident_id, version, requested_at)
SELECT UUID(), id, incident_id, version, created_at FROM diagnosis_tasks
WHERE status IN ('PENDING', 'RUNNING');
