ALTER TABLE attachment
  ADD COLUMN org_id VARCHAR(128) NOT NULL DEFAULT 'default';
CREATE INDEX attachment_org_created_idx ON attachment (org_id, created_at DESC);
