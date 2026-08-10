ALTER TABLE audit_event
  ADD COLUMN organization_id varchar(128) NOT NULL DEFAULT 'default';

CREATE INDEX audit_event_organization_occurred_idx
  ON audit_event(organization_id, occurred_at DESC);
