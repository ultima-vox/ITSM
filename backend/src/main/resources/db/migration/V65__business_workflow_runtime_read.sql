-- Business-module viewers need read-only workflow metadata to render the same
-- transition policy enforced server-side. No workflow write grant is added.
INSERT INTO role_permission (role_id, permission_id)
SELECT DISTINCT viewer.role_id, workflow_read.id
FROM role_permission viewer
JOIN permission object_read ON object_read.id = viewer.permission_id
JOIN permission workflow_read ON workflow_read.permission_key = 'workflow.read'
WHERE object_read.permission_key IN ('problem.read', 'change.read')
ON CONFLICT DO NOTHING;
