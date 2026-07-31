-- Attachment metadata (bytes in object storage via AttachmentStorage)
CREATE TABLE IF NOT EXISTS attachment (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    filename      varchar(255)  NOT NULL,
    content_type  varchar(200)  NOT NULL,
    size_bytes    bigint        NOT NULL CHECK (size_bytes >= 0),
    storage_key   varchar(500)  NOT NULL UNIQUE,
    uploaded_by   varchar(128)  NOT NULL,
    created_at    timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS attachment_uploaded_by_idx ON attachment (uploaded_by, created_at DESC);

-- Permissions: attachments + translation admin
INSERT INTO permission (permission_key, description) VALUES
    ('attachment.read', 'Read attachment metadata and download content'),
    ('attachment.write', 'Upload attachments'),
    ('admin.translations', 'Administer UI translation catalog'),
    ('metadata.write', 'Write object/form/workflow/translation metadata')
ON CONFLICT (permission_key) DO NOTHING;

-- ADMIN gets every permission via V10 cross-join only for rows present at seed time;
-- grant new keys explicitly (admin.full already short-circuits checks).
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.role_key = 'ADMIN'
  AND p.permission_key IN (
    'attachment.read', 'attachment.write', 'admin.translations', 'metadata.write'
  )
ON CONFLICT DO NOTHING;

-- Service desk agents may upload and read attachments
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
CROSS JOIN permission p
WHERE r.role_key IN ('SERVICE_DESK_AGENT', 'SERVICE_DESK_MANAGER')
  AND p.permission_key IN ('attachment.read', 'attachment.write')
ON CONFLICT DO NOTHING;
