-- Service announcements: one broadcast that reaches every operator at once, instead of a
-- major incident being retold in each ticket.
CREATE TABLE announcement (
  id uuid PRIMARY KEY,
  org_id varchar(128) NOT NULL,
  title varchar(240) NOT NULL,
  body varchar(8000) NOT NULL,
  severity varchar(16) NOT NULL CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
  audience varchar(16) NOT NULL CHECK (audience IN ('ALL', 'AGENTS', 'REQUESTERS')),
  starts_at timestamptz NOT NULL,
  ends_at timestamptz,
  published boolean NOT NULL DEFAULT false,
  dismissible boolean NOT NULL DEFAULT true,
  link_url varchar(500),
  created_by varchar(128) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  version bigint NOT NULL DEFAULT 0,
  CHECK (ends_at IS NULL OR ends_at > starts_at)
);
CREATE INDEX announcement_active_idx ON announcement (org_id, published, starts_at DESC);

INSERT INTO permission (permission_key, description) VALUES
  ('announcement.read', 'Read the announcements addressed to you'),
  ('announcement.admin', 'Create, publish and retire announcements')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.permission_key = 'announcement.read'
WHERE r.role_key IN ('ADMIN', 'SERVICE_DESK_AGENT', 'SERVICE_DESK_MANAGER', 'CHANGE_MANAGER', 'REQUESTER')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.permission_key = 'announcement.admin'
WHERE r.role_key IN ('ADMIN', 'SERVICE_DESK_MANAGER')
ON CONFLICT DO NOTHING;
