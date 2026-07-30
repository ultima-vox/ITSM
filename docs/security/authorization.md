# Authorization baseline

All business routes require a validated OIDC JWT. Keycloak realm roles are mapped to `ROLE_<role>` and OAuth scopes to `SCOPE_<scope>`. `PermissionChecker` is deny-by-default; the initial policy accepts one of:

- `ROLE_itsm_admin`;
- role `ROLE_itsm_<permission>` (for example `ROLE_itsm_work_item_read`);
- scope `itsm.<permission>` (for example `itsm.work-item.read`).

Controllers explicitly ask `AccessControl` before reading or mutating domain data. This check is server-side and independent of UI visibility. The next policy adapter will resolve persisted RBAC grants, ownership and CI/service scopes; it must preserve default denial and emit authorization audit events.
