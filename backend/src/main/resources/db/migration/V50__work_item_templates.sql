CREATE TABLE work_item_template (
  id UUID PRIMARY KEY,
  org_id VARCHAR(128) NOT NULL,
  name VARCHAR(160) NOT NULL,
  type VARCHAR(32) NOT NULL CHECK (type IN ('INCIDENT', 'SERVICE_REQUEST')),
  title VARCHAR(240) NOT NULL,
  description VARCHAR(12000) NOT NULL,
  service VARCHAR(100) NOT NULL,
  impact VARCHAR(16) NOT NULL CHECK (impact IN ('LOW', 'MEDIUM', 'HIGH')),
  urgency VARCHAR(16) NOT NULL CHECK (urgency IN ('LOW', 'MEDIUM', 'HIGH')),
  team_id VARCHAR(128),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  version BIGINT NOT NULL DEFAULT 0,
  created_by VARCHAR(128) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  UNIQUE (org_id, name)
);

CREATE INDEX work_item_template_org_active_idx
  ON work_item_template(org_id, active, type, name);

INSERT INTO permission(permission_key, description)
VALUES ('work-item.template.manage', 'Create and maintain work item templates')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.role_key IN ('ADMIN', 'SERVICE_DESK_MANAGER')
  AND p.permission_key = 'work-item.template.manage'
ON CONFLICT DO NOTHING;
