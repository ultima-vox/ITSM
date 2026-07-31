ALTER TABLE work_item
    ADD COLUMN IF NOT EXISTS escalated boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN work_item.escalated IS 'True after operator escalate action (impact/urgency raised)';
