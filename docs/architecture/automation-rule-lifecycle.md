# Automation rule lifecycle

Automation rules are tenant-scoped declarative `WHEN / IF / THEN` definitions. Admin create and
update APIs validate stable keys, bounded conditions, event type syntax, and allowlisted actions.
Updates require `expectedVersion`; stale writes fail with HTTP 409. Default rules are overridden
per tenant without mutating default rows. Create, update, enable, and disable operations emit audit
and transactional outbox records.

Runtime dispatch is idempotent per tenant, rule, event, and action type. A claimed action moves from
`STARTED` to `SUCCEEDED` or `FAILED`; bounded failure text supports diagnosis without persisting a
stack trace. Duplicate action types in one rule are rejected because they would share an idempotency
identity. Executable scripts are forbidden; only allowlisted adapters are accepted. Built-in adapters
are `notify`, `log`, and `index`; business capabilities are contributed by modules through the
`AutomationActionHandler` extension point (e.g. `assign` for service desk work items) and are
validated against registered handlers at rule save time. Handler parameters may reference the
triggering event with `{{data.field}}` or `{{event.field}}` placeholders. `escalate` (contributed by
the service desk module) raises a work item to CRITICAL, flags it escalated, and notifies its
assignee; it is driven by `sla.breached` events, defaulting the target work item to
`{{data.aggregateId}}`. The default tenant ships a rule (`sla.escalate.breach`, seeded by migration)
that escalates every breached SLA clock.

Failed actions are retried automatically. The runner records `FAILED` and schedules a retry row
holding a snapshot of the event and action parameters (`automation_action_retry`); a scheduled sweep
(`itsm.automation.retry-interval`, default 1m) re-drives due rows with exponential backoff
(`itsm.automation.backoff-base`/`backoff-max`, default 30s→10m). A successful retry rewrites the
action-log row to `SUCCEEDED` with its attempt count, so the execution history shows the eventual
outcome. Rows that exhaust the attempt budget (`itsm.automation.max-attempts`, default 5) are
quarantined and stop being polled — the analog of the outbox relay's quarantine — so a poison
action can never loop forever. Re-drives are idempotent: at most one retry row exists per
(tenant, rule, event, action), and the original idempotency key is preserved.
