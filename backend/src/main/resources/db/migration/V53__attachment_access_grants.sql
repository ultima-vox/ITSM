CREATE TABLE attachment_access_grant (
  attachment_id UUID NOT NULL REFERENCES attachment(id) ON DELETE CASCADE,
  org_id VARCHAR(128) NOT NULL,
  subject_id VARCHAR(128) NOT NULL,
  source_type VARCHAR(64) NOT NULL,
  source_id VARCHAR(128) NOT NULL,
  granted_by VARCHAR(128) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (attachment_id, org_id, subject_id, source_type, source_id)
);

CREATE INDEX attachment_access_subject_idx
  ON attachment_access_grant(org_id, subject_id, attachment_id);

INSERT INTO attachment_access_grant(
  attachment_id, org_id, subject_id, source_type, source_id, granted_by, created_at
)
SELECT DISTINCT wa.attachment_id, wi.org_id, wi.requester_id, 'work-item', wi.id::text,
       wa.linked_by, wa.linked_at
FROM work_item_attachment wa
JOIN work_item wi ON wi.id = wa.work_item_id
WHERE wi.requester_id IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO permission(permission_key, description)
VALUES ('attachment.read.any', 'Read attachments across authorized service records')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.role_key IN ('ADMIN', 'SERVICE_DESK_AGENT', 'SERVICE_DESK_MANAGER')
  AND p.permission_key = 'attachment.read.any'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.role_key = 'REQUESTER'
  AND p.permission_key IN ('attachment.read', 'attachment.write')
ON CONFLICT DO NOTHING;
