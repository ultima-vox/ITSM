-- Service desk roles held write/fulfil grants without the matching read grants, so the
-- knowledge, catalog, CMDB, asset, change, and problem screens returned 403 for every
-- agent and manager. Grant the read side of the permissions those roles already act on.

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.permission_key IN (
    'knowledge.read',
    'catalog.read',
    'cmdb.read',
    'asset.read',
    'change.read',
    'problem.read'
)
WHERE r.role_key IN ('SERVICE_DESK_AGENT', 'SERVICE_DESK_MANAGER')
ON CONFLICT DO NOTHING;
