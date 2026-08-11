CREATE TABLE field_access_policy (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id               varchar(128) NOT NULL,
    object_type          varchar(100) NOT NULL,
    field_key            varchar(120) NOT NULL,
    operation            varchar(20) NOT NULL CHECK (operation IN ('READ','WRITE')),
    object_state         varchar(80),
    required_permission  varchar(150) NOT NULL REFERENCES permission(permission_key),
    active               boolean NOT NULL DEFAULT true,
    created_at           timestamptz NOT NULL DEFAULT now(),
    UNIQUE (org_id, object_type, field_key, operation, object_state, required_permission)
);

CREATE INDEX field_access_policy_lookup_idx
    ON field_access_policy (org_id, object_type, field_key, operation)
    WHERE active;

INSERT INTO permission(permission_key, description)
VALUES ('work-item.resolve', 'Set work item resolution fields')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO role_permission(role_id, permission_id)
SELECT r.id,p.id FROM role r CROSS JOIN permission p
WHERE r.role_key IN ('ADMIN','SERVICE_DESK_AGENT','SERVICE_DESK_MANAGER')
  AND p.permission_key='work-item.resolve'
ON CONFLICT DO NOTHING;

INSERT INTO field_access_policy
  (org_id,object_type,field_key,operation,object_state,required_permission)
VALUES
  ('*','work-item','resolutionCode','WRITE','RESOLVED','work-item.resolve'),
  ('*','work-item','resolutionNotes','WRITE','RESOLVED','work-item.resolve');

