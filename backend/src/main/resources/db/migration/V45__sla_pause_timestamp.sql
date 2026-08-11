ALTER TABLE sla_clock ADD COLUMN paused_at TIMESTAMPTZ;

UPDATE sla_clock SET paused_at = updated_at WHERE state = 'PAUSED';

ALTER TABLE sla_clock ADD CONSTRAINT sla_clock_pause_state_check
    CHECK ((state = 'PAUSED' AND paused_at IS NOT NULL) OR (state <> 'PAUSED' AND paused_at IS NULL));
