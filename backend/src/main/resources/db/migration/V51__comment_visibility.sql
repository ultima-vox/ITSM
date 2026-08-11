ALTER TABLE work_item_comment
  ADD COLUMN internal BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE audit_event
SET after_state = jsonb_set(COALESCE(after_state, '{}'::jsonb), '{internal}', 'true'::jsonb)
WHERE action = 'work-item.comment-added'
  AND NOT COALESCE(after_state, '{}'::jsonb) ? 'internal';

INSERT INTO permission(permission_key, description)
VALUES ('work-item.comment.internal', 'Create and view internal work item notes')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.role_key IN ('ADMIN', 'SERVICE_DESK_AGENT', 'SERVICE_DESK_MANAGER')
  AND p.permission_key = 'work-item.comment.internal'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.role_key = 'REQUESTER' AND p.permission_key = 'work-item.comment'
ON CONFLICT DO NOTHING;
