ALTER TABLE principal_role ADD COLUMN org_id varchar(128) NOT NULL DEFAULT 'default';
ALTER TABLE principal_role DROP CONSTRAINT principal_role_subject_id_role_id_key;
ALTER TABLE principal_role ADD CONSTRAINT principal_role_org_subject_role_unique
    UNIQUE (org_id, subject_id, role_id);
DROP INDEX IF EXISTS principal_role_subject_idx;
CREATE INDEX principal_role_org_subject_idx ON principal_role (org_id, subject_id);

ALTER TABLE rbac_grant ADD COLUMN org_id varchar(128) NOT NULL DEFAULT 'default';
CREATE INDEX rbac_grant_org_subject_idx
    ON rbac_grant (org_id, subject_type, subject_id, permission);
