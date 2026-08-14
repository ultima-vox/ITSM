# SLA engine baseline

SLA is business-time based, not a simple timestamp subtraction. `WorkingCalendar` carries a timezone, permitted days, a same-day working window and explicit holidays. `SlaDeadlineCalculator` consumes only business minutes and therefore correctly crosses closed periods and weekends. The initial contract rejects negative targets and malformed calendars.

`SlaClock` represents live measurement state. A scheduler should query indexed RUNNING clocks, emit `sla.warning` / `sla.breached` through the transactional outbox, and write a `sla_clock_history` record on each start, pause, resume, achievement, breach or policy recalculation. A paused clock never silently loses elapsed history.

## Breach and warning sweep

`SlaBreachScheduler` runs on a fixed delay (`itsm.sla.breach-interval`, default `PT1M`, with a matching initial delay) and, for every organization holding a RUNNING clock that is due or inside its warning window, invokes `SlaService.detectBreaches` then `SlaService.detectWarnings` inside that tenant's scope.

- **Breach**: a RUNNING clock whose `due_at` has passed is marked `BREACHED`, gets a `BREACH` history record, and emits `sla.breached` (data: `policyKey`, `aggregateId`, `metric`, `dueAt`).
- **Warning**: a RUNNING clock whose `warning_at` has passed but whose `due_at` has not, and which has not been warned before, is marked with `warned_at`, gets a `WARN` history record, and emits `sla.warning`. The `warned_at` marker makes the warning idempotent: each clock warns at most once. Clocks already past `due_at` are breached, not warned.
- **Tenancy**: events carry the organization of the clock owner because the sweep runs per organization; tenant-scoped automation rules therefore fire on their own `sla.warning` / `sla.breached` events, and one tenant's clocks never affect another's.
- Events flow through the transactional outbox (`outbox_event`) so the relay and automation run exactly-once per event.

## Breach response

The default tenant ships an automation rule (`sla.escalate.breach`) that fires the allowlisted
`escalate` action on every `sla.breached` event: the work item owning the breached clock is raised to
CRITICAL, flagged `escalated`, moved to IN_PROGRESS if still NEW, reindexed, and its assignee is
notified. Tenants may override the rule (same key) or disable it. The escalation is idempotent per
event via the automation action log.

## Clock lifecycle

Clocks start when a work item is created (response SLA, `work-item.response.default` /
`response` metric). When a work item is resolved, closed or cancelled, `TransitionWorkItem` stops
every active clock for that item as `ACHIEVED` (with an `ACHIEVED` history record) in the same
transaction, so closed items never keep running clocks and breach/reporting counts stay correct.
Reopening a resolved item does not restart a stopped clock; a fresh policy measurement starts with
the next matching creation.
