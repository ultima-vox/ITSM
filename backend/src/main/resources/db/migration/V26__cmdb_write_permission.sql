INSERT INTO permission (permission_key, description) VALUES
    ('cmdb.write', 'Create and update configuration items and relationships')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.role_key = 'ADMIN'
  AND p.permission_key = 'cmdb.write'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.role_key IN ('SERVICE_DESK_MANAGER', 'CHANGE_MANAGER')
  AND p.permission_key = 'cmdb.write'
ON CONFLICT DO NOTHING;
