# Temporary role delegation

Administrators with `rbac.delegate` may create and revoke temporary role delegations
through `/api/v1/rbac/delegations` or **Admin → Roles & access → Delegations**.

Enforced invariants:

- organization comes only from trusted JWT context;
- delegator must directly hold requested role; delegated roles cannot be re-delegated;
- self-delegation is rejected;
- `ADMIN` and any role carrying `admin.full`, `rbac.write`, or `rbac.delegate` cannot be delegated;
- validity window must be positive, cannot start materially in past, and is capped at 90 days;
- evaluator considers only started, unexpired, non-revoked rows;
- creation and revocation write audit and transactional outbox records.

Revocation is immediate for subsequent authorization checks. Existing access tokens do
not embed delegated permissions; server resolves them from PostgreSQL on every decision.
