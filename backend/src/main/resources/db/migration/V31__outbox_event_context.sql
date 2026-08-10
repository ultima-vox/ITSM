ALTER TABLE outbox_event
  ADD COLUMN schema_version integer NOT NULL DEFAULT 1,
  ADD COLUMN correlation_id uuid,
  ADD COLUMN causation_id uuid,
  ADD COLUMN organization_id varchar(128) NOT NULL DEFAULT 'default',
  ADD COLUMN actor_id varchar(128) NOT NULL DEFAULT 'system';

UPDATE outbox_event
SET correlation_id = (payload ->> 'correlationId')::uuid
WHERE correlation_id IS NULL AND payload ->> 'correlationId' IS NOT NULL;

UPDATE outbox_event
SET correlation_id = id
WHERE correlation_id IS NULL;

ALTER TABLE outbox_event
  ALTER COLUMN correlation_id SET NOT NULL;

CREATE INDEX outbox_event_correlation_idx ON outbox_event(correlation_id);
CREATE INDEX outbox_event_organization_idx ON outbox_event(organization_id, occurred_at DESC);
