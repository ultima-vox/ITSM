INSERT INTO permission (permission_key, description) VALUES
    ('knowledge.write', 'Create, edit, and publish knowledge articles')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.role_key = 'ADMIN'
  AND p.permission_key = 'knowledge.write'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.role_key IN ('SERVICE_DESK_MANAGER', 'SERVICE_DESK_AGENT')
  AND p.permission_key = 'knowledge.write'
ON CONFLICT DO NOTHING;
