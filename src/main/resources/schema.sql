CREATE TABLE IF NOT EXISTS incidents (
    id VARCHAR(36) PRIMARY KEY,
    dedup_key VARCHAR(255) NOT NULL UNIQUE,
    title VARCHAR(500) NOT NULL,
    service VARCHAR(120) NOT NULL,
    environment VARCHAR(80) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    severity VARCHAR(10) NOT NULL,
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    error_count BIGINT NOT NULL,
    assignee VARCHAR(120) NOT NULL,
    resolution TEXT NOT NULL,
    INDEX idx_incidents_started_at (started_at DESC),
    INDEX idx_incidents_service_environment (service, environment)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS diagnosis_tasks (
    id VARCHAR(36) PRIMARY KEY,
    incident_id VARCHAR(36) NOT NULL,
    version INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    failure_reason TEXT NOT NULL,
    UNIQUE KEY uk_diagnosis_tasks_incident_version (incident_id, version),
    INDEX idx_diagnosis_tasks_incident (incident_id, created_at DESC),
    CONSTRAINT fk_diagnosis_tasks_incident FOREIGN KEY (incident_id) REFERENCES incidents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS diagnosis_reports (
    id VARCHAR(36) PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL,
    version INT NOT NULL,
    root_cause TEXT NOT NULL,
    confidence DOUBLE NOT NULL,
    evidence TEXT NOT NULL,
    recommendations TEXT NOT NULL,
    tool_calls TEXT NOT NULL,
    generated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_diagnosis_reports_task_version (task_id, version),
    CONSTRAINT fk_diagnosis_reports_task FOREIGN KEY (task_id) REFERENCES diagnosis_tasks(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
