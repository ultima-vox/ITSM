-- Work-item viewers need read-only policy metadata to render server-authoritative
-- workflow transitions and SLA targets. Write permissions remain admin/manager scoped.
INSERT INTO role_permission (role_id, permission_id)
SELECT DISTINCT viewer.role_id, runtime_permission.id
FROM role_permission viewer
JOIN permission work_item_read ON work_item_read.id = viewer.permission_id
JOIN permission runtime_permission
  ON runtime_permission.permission_key IN ('workflow.read', 'sla.read')
WHERE work_item_read.permission_key = 'work-item.read'
ON CONFLICT DO NOTHING;
