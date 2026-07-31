-- Full-text search API permission (OpenSearch or JDBC projection).
INSERT INTO permission (permission_key, description) VALUES
    ('search.read', 'Full-text search across indexed projections')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.role_key IN ('ADMIN', 'SERVICE_DESK_AGENT', 'SERVICE_DESK_MANAGER', 'REQUESTER', 'CHANGE_MANAGER')
  AND p.permission_key = 'search.read'
ON CONFLICT DO NOTHING;
