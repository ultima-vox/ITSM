-- Admin read permissions for SLA policies and automation rules (list APIs).
INSERT INTO permission (permission_key, description) VALUES
    ('sla.read', 'Read SLA policies and clocks'),
    ('sla.write', 'Create or update SLA policies'),
    ('automation.read', 'Read automation rules and action logs'),
    ('automation.write', 'Create or update automation rules')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.role_key = 'ADMIN'
  AND p.permission_key IN ('sla.read', 'sla.write', 'automation.read', 'automation.write')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.permission_key IN ('sla.read', 'automation.read')
WHERE r.role_key = 'SERVICE_DESK_MANAGER'
ON CONFLICT DO NOTHING;

-- Local dev principal used by profile 'dev'
INSERT INTO principal_role (subject_id, role_id)
SELECT 'dev-local', r.id
FROM role r
WHERE r.role_key = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM principal_role pr
      WHERE pr.subject_id = 'dev-local' AND pr.role_id = r.id
  );
