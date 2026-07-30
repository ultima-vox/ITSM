ALTER TABLE outbox_event ADD COLUMN last_error varchar(1000);
CREATE INDEX outbox_event_relay_idx ON outbox_event(occurred_at) WHERE published_at IS NULL;
