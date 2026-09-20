ALTER TABLE incidents
    ADD INDEX idx_incidents_status_started (status, started_at DESC),
    ADD INDEX idx_incidents_type_started (incident_type, started_at DESC),
    ADD INDEX idx_incidents_assignee_started (assignee, started_at DESC),
    ADD INDEX idx_incidents_service_environment_started (service, environment, started_at DESC);

ALTER TABLE dead_letter_messages
    ADD COLUMN failure_type VARCHAR(255) NOT NULL DEFAULT 'UNKNOWN' AFTER payload,
    ADD COLUMN replay_owner VARCHAR(64) NOT NULL DEFAULT '' AFTER replayed_at,
    ADD COLUMN replay_lease_until TIMESTAMP(6) NULL AFTER replay_owner,
    ADD INDEX idx_dead_letter_topic_failed (original_topic, failed_at DESC),
    ADD INDEX idx_dead_letter_failure_type_failed (failure_type, failed_at DESC),
    ADD INDEX idx_dead_letter_status_failed (replayed_at, failed_at DESC),
    ADD INDEX idx_dead_letter_replay_lease (replayed_at, replay_lease_until);

CREATE TABLE dead_letter_replay_attempts (
    id VARCHAR(36) PRIMARY KEY,
    dead_letter_id VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    failure_reason TEXT NULL,
    INDEX idx_dead_letter_replay_attempts_message (dead_letter_id, started_at DESC),
    CONSTRAINT fk_dead_letter_replay_attempts_message
        FOREIGN KEY (dead_letter_id) REFERENCES dead_letter_messages(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
