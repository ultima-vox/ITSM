# Automation rule lifecycle

Automation rules are tenant-scoped declarative `WHEN / IF / THEN` definitions. Admin create and
update APIs validate stable keys, bounded conditions, event type syntax, and allowlisted actions.
Updates require `expectedVersion`; stale writes fail with HTTP 409. Default rules are overridden
per tenant without mutating default rows. Create, update, enable, and disable operations emit audit
and transactional outbox records.

Runtime dispatch is idempotent per tenant, rule, event, and action type. A claimed action moves from
`STARTED` to `SUCCEEDED` or `FAILED`; bounded failure text supports diagnosis without persisting a
stack trace. Duplicate action types in one rule are rejected because they would share an idempotency
identity. Executable scripts are forbidden; only `notify`, `log`, and `index` adapters are accepted.
