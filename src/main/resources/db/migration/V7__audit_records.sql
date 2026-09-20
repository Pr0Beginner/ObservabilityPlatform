CREATE TABLE audit_records (
    id VARCHAR(36) PRIMARY KEY,
    actor VARCHAR(120) NOT NULL,
    roles JSON NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(255) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    before_state JSON NOT NULL,
    after_state JSON NOT NULL,
    failure_reason TEXT NULL,
    INDEX idx_audit_records_occurred_at (occurred_at),
    INDEX idx_audit_records_actor_time (actor, occurred_at),
    INDEX idx_audit_records_action_time (action_type, occurred_at),
    INDEX idx_audit_records_target (target_type, target_id, occurred_at),
    INDEX idx_audit_records_outcome_time (outcome, occurred_at)
);
