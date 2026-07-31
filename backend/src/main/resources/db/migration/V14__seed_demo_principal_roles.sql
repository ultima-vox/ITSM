-- Demo principal_role rows for Keycloak users from infra/keycloak/itsm-realm.json.
-- Subject IDs are fixed UUIDs assigned in the realm import so JWT `sub` matches RBAC.

INSERT INTO principal_role (subject_id, role_id)
SELECT 'a0000000-0000-4000-8000-000000000001', r.id
FROM role r
WHERE r.role_key = 'SERVICE_DESK_AGENT'
  AND NOT EXISTS (
      SELECT 1 FROM principal_role pr
      WHERE pr.subject_id = 'a0000000-0000-4000-8000-000000000001'
        AND pr.role_id = r.id
  );

INSERT INTO principal_role (subject_id, role_id)
SELECT 'a0000000-0000-4000-8000-000000000002', r.id
FROM role r
WHERE r.role_key = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM principal_role pr
      WHERE pr.subject_id = 'a0000000-0000-4000-8000-000000000002'
        AND pr.role_id = r.id
  );

INSERT INTO principal_role (subject_id, role_id)
SELECT 'a0000000-0000-4000-8000-000000000003', r.id
FROM role r
WHERE r.role_key = 'REQUESTER'
  AND NOT EXISTS (
      SELECT 1 FROM principal_role pr
      WHERE pr.subject_id = 'a0000000-0000-4000-8000-000000000003'
        AND pr.role_id = r.id
  );
