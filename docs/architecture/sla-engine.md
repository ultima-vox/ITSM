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

## Warning response

The default tenant ships a second rule (`sla.warning.notify`) that fires the allowlisted
`sla-warning-notify` action on every `sla.warning` event. The action resolves the owning work item
(from the event's `aggregateId`), looks up its owner — the assignee, or the requester when
unassigned — and sends an in-app `sla.warning` notification carrying the item number, title and
deadline. Because the sweep marks each clock `warned_at` before emitting the event, the warning
(and thus the notification) is delivered at most once per clock. Items without any owner are
skipped; recipient channel preferences still apply.

## Clock lifecycle

Clocks start when a work item is created (response SLA, `work-item.response.default` /
`response` metric). When a work item is resolved, closed or cancelled, `TransitionWorkItem` stops
every active clock for that item as `ACHIEVED` (with an `ACHIEVED` history record) in the same
transaction, so closed items never keep running clocks and breach/reporting counts stay correct.
Reopening a resolved item does not restart a stopped clock; a fresh policy measurement starts with
the next matching creation.

## Pause / resume

When a work item enters a pauseable business state, the response clock must not keep counting.
`TransitionWorkItem` decides this per transition: after any non-terminal transition it asks
`SlaService.isPauseable(workItemId, targetState)` — true when the item's active clock policy lists
the target state in `pauseStates` — and then either pauses (`SlaService.pauseForState`) or resumes
(`SlaService.resumeAll`). The default `work-item.response.default` policy lists `PENDING`, so moving
IN_PROGRESS → PENDING pauses the clock and PENDING → IN_PROGRESS resumes it, recalculating the
deadline from business time actually spent so a wait never extends the countdown. Pausing/resuming
writes `PAUSE` / `RESUME` history records; resume rewrites `due_at` / `warning_at` from the
calendar. Terminal transitions still achieve clocks as described above.

## API / UI visibility

`WorkItemResponse` exposes the live response-clock status as `slaState` (`on_track | at_risk |
breached | met`) plus `slaDueAt` / `slaWarningAt`, derived by `WorkItemSlaStateResolver` from the
item's active clock (terminal items report `met`; items without a clock return no snapshot and the
UI falls back to the legacy derived state). The API therefore drives the SLA chrome on the work-item
detail instead of client-side guesses.
