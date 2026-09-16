CREATE TABLE IF NOT EXISTS incidents (
    id VARCHAR(36) PRIMARY KEY,
    dedup_key VARCHAR(255) NOT NULL UNIQUE,
    title VARCHAR(500) NOT NULL,
    service VARCHAR(120) NOT NULL,
    environment VARCHAR(80) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    severity VARCHAR(10) NOT NULL,
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    error_count BIGINT NOT NULL,
    assignee VARCHAR(120) NOT NULL,
    resolution TEXT NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS idx_incidents_started_at ON incidents (started_at DESC);
CREATE INDEX IF NOT EXISTS idx_incidents_service_environment ON incidents (service, environment);

CREATE TABLE IF NOT EXISTS diagnosis_tasks (
    id VARCHAR(36) PRIMARY KEY,
    incident_id VARCHAR(36) NOT NULL REFERENCES incidents(id),
    version INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    failure_reason TEXT NOT NULL DEFAULT '',
    UNIQUE (incident_id, version)
);

CREATE INDEX IF NOT EXISTS idx_diagnosis_tasks_incident ON diagnosis_tasks (incident_id, created_at DESC);

CREATE TABLE IF NOT EXISTS diagnosis_reports (
    id VARCHAR(36) PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL REFERENCES diagnosis_tasks(id),
    version INTEGER NOT NULL,
    root_cause TEXT NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    evidence TEXT NOT NULL,
    recommendations TEXT NOT NULL,
    tool_calls TEXT NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (task_id, version)
);
