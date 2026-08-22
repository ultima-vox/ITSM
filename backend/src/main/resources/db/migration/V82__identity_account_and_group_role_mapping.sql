-- External IdP accounts and configurable group → ITSM role mapping (OIDC; AD/LDAP later).
CREATE TABLE identity_account (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    idp         text NOT NULL,
    external_id text NOT NULL,
    subject_id  text NOT NULL,
    enabled     boolean NOT NULL DEFAULT true,
    last_sync   timestamptz,
    UNIQUE (idp, external_id)
);
CREATE INDEX identity_account_subject_idx ON identity_account (subject_id);

CREATE TABLE group_role_mapping (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    idp_group   text NOT NULL UNIQUE,
    role_name   text NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT group_role_mapping_role_name_fkey
        FOREIGN KEY (role_name) REFERENCES role (role_key)
);
CREATE INDEX group_role_mapping_role_idx ON group_role_mapping (role_name);

INSERT INTO group_role_mapping (idp_group, role_name) VALUES
    ('ITSM-Users', 'REQUESTER'),
    ('ITSM-ServiceDesk', 'SERVICE_DESK_AGENT'),
    ('ITSM-ServiceDesk-Managers', 'SERVICE_DESK_MANAGER'),
    ('ITSM-Change-Managers', 'CHANGE_MANAGER'),
    ('ITSM-CAB', 'CAB_MEMBER'),
    ('ITSM-Admins', 'ADMIN');
