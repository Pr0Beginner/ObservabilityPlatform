ALTER TABLE incidents
    ADD COLUMN version BIGINT NOT NULL DEFAULT 1 AFTER last_observed_window;

ALTER TABLE incident_notifications
    ADD COLUMN lease_owner VARCHAR(64) NOT NULL DEFAULT '' AFTER last_error,
    ADD COLUMN lease_until TIMESTAMP(6) NULL AFTER lease_owner,
    ADD INDEX idx_incident_notifications_lease (status, lease_until, updated_at);
