ALTER TABLE metric_trace_samples
    ADD INDEX idx_metric_trace_samples_start (window_start);

ALTER TABLE dead_letter_messages
    ADD INDEX idx_dead_letter_replayed_at (replayed_at);
