INSERT INTO permission(permission_key, description)
VALUES ('catalog.admin', 'Configure catalog items and bundles')
ON CONFLICT(permission_key) DO NOTHING;

INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.role_key='ADMIN' AND p.permission_key='catalog.admin'
ON CONFLICT DO NOTHING;
