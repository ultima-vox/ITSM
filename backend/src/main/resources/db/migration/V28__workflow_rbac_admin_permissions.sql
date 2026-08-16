-- Admin read permissions for workflow definitions and RBAC catalog.
INSERT INTO permission (permission_key, description) VALUES
    ('workflow.read', 'Read workflow definitions'),
    ('workflow.write', 'Create or activate workflow definition versions'),
    ('rbac.read', 'Read roles, permissions, and principal assignments'),
    ('rbac.write', 'Assign roles to principals')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.role_key = 'ADMIN'
  AND p.permission_key IN ('workflow.read', 'workflow.write', 'rbac.read', 'rbac.write')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.permission_key IN ('workflow.read', 'rbac.read')
WHERE r.role_key = 'SERVICE_DESK_MANAGER'
ON CONFLICT DO NOTHING;
