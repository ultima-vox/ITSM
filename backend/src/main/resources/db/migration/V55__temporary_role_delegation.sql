CREATE TABLE role_delegation (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id        varchar(128) NOT NULL,
    delegator_id  varchar(255) NOT NULL,
    delegatee_id  varchar(255) NOT NULL,
    role_id       uuid NOT NULL REFERENCES role(id),
    starts_at     timestamptz NOT NULL,
    expires_at    timestamptz NOT NULL,
    reason        varchar(1000) NOT NULL,
    created_by    varchar(255) NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    revoked_by    varchar(255),
    revoked_at    timestamptz,
    CONSTRAINT role_delegation_people_ck CHECK (delegator_id <> delegatee_id),
    CONSTRAINT role_delegation_window_ck CHECK (expires_at > starts_at),
    CONSTRAINT role_delegation_revocation_ck CHECK (
      (revoked_at IS NULL AND revoked_by IS NULL) OR
      (revoked_at IS NOT NULL AND revoked_by IS NOT NULL)
    )
);

CREATE INDEX role_delegation_delegatee_active_idx
    ON role_delegation (org_id, delegatee_id, starts_at, expires_at)
    WHERE revoked_at IS NULL;
CREATE INDEX role_delegation_delegator_idx
    ON role_delegation (org_id, delegator_id, created_at DESC);

INSERT INTO permission(permission_key, description)
VALUES ('rbac.delegate', 'Create and revoke temporary role delegations')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.role_key = 'ADMIN' AND p.permission_key = 'rbac.delegate'
ON CONFLICT DO NOTHING;

