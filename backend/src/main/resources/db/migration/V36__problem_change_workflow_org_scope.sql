ALTER TABLE problem
  ADD COLUMN org_id VARCHAR(128) NOT NULL DEFAULT 'default';
CREATE INDEX problem_org_updated_idx ON problem (org_id, updated_at DESC);

ALTER TABLE change_request
  ADD COLUMN org_id VARCHAR(128) NOT NULL DEFAULT 'default';
CREATE INDEX change_request_org_updated_idx ON change_request (org_id, updated_at DESC);
CREATE INDEX change_request_org_schedule_idx ON change_request (org_id, status, planned_start);

ALTER TABLE workflow_instance
  ADD COLUMN org_id VARCHAR(128) NOT NULL DEFAULT 'default';
ALTER TABLE workflow_instance
  DROP CONSTRAINT workflow_instance_object_type_object_id_key;
ALTER TABLE workflow_instance
  ADD CONSTRAINT workflow_instance_org_object_key UNIQUE (org_id, object_type, object_id);
CREATE INDEX workflow_instance_org_state_idx ON workflow_instance (org_id, object_type, state);
