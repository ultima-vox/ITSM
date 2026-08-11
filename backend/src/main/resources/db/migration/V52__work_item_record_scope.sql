INSERT INTO permission(permission_key, description)
VALUES ('work-item.read.any', 'Read work items owned by any requester')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.role_key IN ('ADMIN', 'SERVICE_DESK_AGENT', 'SERVICE_DESK_MANAGER', 'CHANGE_MANAGER')
  AND p.permission_key = 'work-item.read.any'
ON CONFLICT DO NOTHING;
