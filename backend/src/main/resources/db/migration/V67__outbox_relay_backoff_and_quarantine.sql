ALTER TABLE outbox_event
  ADD COLUMN attempted_at timestamptz,
  ADD COLUMN next_attempt_at timestamptz,
  ADD COLUMN quarantined_at timestamptz;

CREATE INDEX outbox_event_due_idx
  ON outbox_event (occurred_at)
  WHERE published_at IS NULL AND quarantined_at IS NULL;
