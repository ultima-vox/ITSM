-- Admin audit trail list API
INSERT INTO permission (permission_key, description) VALUES
    ('audit.read', 'Read platform audit event trail')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.role_key = 'ADMIN'
  AND p.permission_key = 'audit.read'
ON CONFLICT DO NOTHING;

-- Managers may review operator audit for service desk oversight
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.role_key = 'SERVICE_DESK_MANAGER'
  AND p.permission_key = 'audit.read'
ON CONFLICT DO NOTHING;

CREATE INDEX IF NOT EXISTS audit_event_occurred_idx
    ON audit_event (occurred_at DESC);

CREATE INDEX IF NOT EXISTS audit_event_action_idx
    ON audit_event (action, occurred_at DESC);
