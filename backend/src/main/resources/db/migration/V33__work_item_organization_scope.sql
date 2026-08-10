ALTER TABLE work_item
  ADD COLUMN org_id VARCHAR(128) NOT NULL DEFAULT 'default';

CREATE INDEX idx_work_item_org_updated
  ON work_item (org_id, updated_at DESC);

CREATE INDEX idx_work_item_org_number
  ON work_item (org_id, number);
