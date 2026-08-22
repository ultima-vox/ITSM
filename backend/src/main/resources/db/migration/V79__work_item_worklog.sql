-- Time tracking: agents log the effort they actually spent on a work item, so effort
-- reporting and billing stop depending on the SLA clock, which measures elapsed time.
CREATE TABLE work_item_worklog (
  id uuid PRIMARY KEY,
  org_id varchar(128) NOT NULL,
  work_item_id uuid NOT NULL REFERENCES work_item(id) ON DELETE CASCADE,
  author_subject varchar(128) NOT NULL,
  minutes integer NOT NULL CHECK (minutes > 0 AND minutes <= 1440),
  started_at timestamptz NOT NULL,
  note varchar(4000),
  billable boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX work_item_worklog_item_idx ON work_item_worklog (org_id, work_item_id, started_at DESC);
CREATE INDEX work_item_worklog_author_idx ON work_item_worklog (org_id, author_subject, started_at DESC);

INSERT INTO permission (permission_key, description) VALUES
  ('work-item.worklog', 'Log and edit own time on a work item'),
  ('work-item.worklog.manage', 'Edit or delete time logged by anyone')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.permission_key = 'work-item.worklog'
WHERE r.role_key IN ('ADMIN', 'SERVICE_DESK_AGENT', 'SERVICE_DESK_MANAGER')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.permission_key = 'work-item.worklog.manage'
WHERE r.role_key IN ('ADMIN', 'SERVICE_DESK_MANAGER')
ON CONFLICT DO NOTHING;
