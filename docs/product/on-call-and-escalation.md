# On-call rotations and escalation policies

An escalation that stops at the current assignee is not an escalation. This module answers two
questions the service desk could not answer before: **who is on call right now**, and **who is
paged next if they stay silent**.

## Rotations

`on_call_schedule` (migration `V80`) holds a rotation:

| Column | Meaning |
| --- | --- |
| `schedule_key` | stable key referenced by escalation steps, unique per organization |
| `rotation_hours` | how long one participant holds the rotation (24 = daily, 168 = weekly) |
| `rotation_start` | when the first participant took it |
| `time_zone` | recorded for display; the rotation maths itself runs on absolute hours |
| `active` | an inactive rotation resolves to nobody rather than a stale name |

`on_call_participant` holds the ordered ring. The participant on call at an instant is
`floor(hours since rotation_start / rotation_hours) mod participants`, so the answer is correct
however far ahead the question is asked. Before the rotation starts, the first participant holds it.

Because the index is computed from absolute hours, a rotation does **not** shift with daylight
saving in `time_zone` — the handover keeps its UTC instant. That is a deliberate simplification,
not a claim of calendar-aware handovers.

## Overrides

`on_call_override` covers a window with a named subject and wins over the rotation for that
window — the "Alice is at the dentist" case. Outside the window the rotation resumes on its own.

## Escalation policies

`escalation_policy` plus `escalation_step` describe the chain: each step has a delay in minutes
and a target that is either a `SUBJECT` (a named person) or a `SCHEDULE` (whoever is on call).
Delays must be non-decreasing — a step cannot fire before the one it escalates from.

`OnCallDirectory.escalationChain(policyKey, at)` resolves the chain to subjects. A step pointing
at a rotation with nobody on call is skipped, so the rest of the chain still fires.

## Where it is used

`EscalateWorkItem` consults the `work-item.escalation` policy after it escalates a work item and
notifies every resolved responder, skipping the current assignee, who is already notified. If no
such policy exists, nothing extra happens — the behaviour before this module.

## API

| Method | Path | Permission |
| --- | --- | --- |
| `GET` | `/api/v1/oncall/schedules` | `oncall.read` |
| `GET` | `/api/v1/oncall/schedules/{key}` | `oncall.read` |
| `GET` | `/api/v1/oncall/schedules/{key}/current?at=` | `oncall.read` |
| `GET` | `/api/v1/oncall/schedules/{key}/overrides` | `oncall.read` |
| `GET` | `/api/v1/oncall/policies` | `oncall.read` |
| `GET` | `/api/v1/oncall/policies/{key}/chain?at=` | `oncall.read` |
| `POST`/`PUT`/`DELETE` | schedules, overrides, policies | `oncall.admin` |

`oncall.read` is granted to `ADMIN`, `SERVICE_DESK_AGENT`, `SERVICE_DESK_MANAGER` and
`CHANGE_MANAGER`; `oncall.admin` to `ADMIN` and `SERVICE_DESK_MANAGER`.

`PUT` replaces a rotation with its full participant ring, and a policy with its full step list —
there is no partial step patch, so a half-applied chain cannot exist.

## Admin screen

`/admin/oncall` lists rotations with who holds each one right now, the override editor per
rotation, and the escalation policies with their resolved step chain.
