# Time tracking on work items

An SLA clock measures elapsed time — how long the customer waited. A worklog measures effort —
how many minutes an agent actually spent. Effort reporting, capacity planning and billing all
need the second number, and it cannot be derived from the first.

## Model

`work_item_worklog` (migration `V79`) holds one row per logged entry:

| Column | Rule |
| --- | --- |
| `minutes` | 1 – 1440, enforced by a check constraint and by the service |
| `started_at` | required, and refused if it is more than five minutes in the future |
| `note` | optional, up to 4000 characters |
| `billable` | flag, rolled up separately from the total |
| `author_subject` | the subject that logged the entry |

Rows are organization-scoped and cascade with their work item.

## Permissions

| Permission | Granted to | Allows |
| --- | --- | --- |
| `work-item.read` | existing service desk roles | reading the log and its totals |
| `work-item.worklog` | `ADMIN`, `SERVICE_DESK_AGENT`, `SERVICE_DESK_MANAGER` | logging time, editing and deleting **own** entries |
| `work-item.worklog.manage` | `ADMIN`, `SERVICE_DESK_MANAGER` | editing and deleting entries logged by anyone |

Authorship is checked in the service, not only in the UI: an agent who edits another agent's
entry without the manage grant gets `409 Conflict` with `Only the author can change this worklog`.

## API

| Method | Path |
| --- | --- |
| `GET` | `/api/v1/work-items/{id}/worklogs` |
| `POST` | `/api/v1/work-items/{id}/worklogs` |
| `PATCH` | `/api/v1/work-items/{id}/worklogs/{worklogId}` |
| `DELETE` | `/api/v1/work-items/{id}/worklogs/{worklogId}` |

The list response carries `items`, `totalMinutes` and `billableMinutes`, so the client never has
to sum the rows itself.

Every write is audited (`work-item.time-logged`, `work-item.time-updated`,
`work-item.time-deleted`) and published to the outbox, so an external billing system can consume
the same events.

## Operator screen

The work item detail view gains a **Time** tab: total, billable share and entry count, the log
itself, and a compact form to add an entry. The form is hidden without `work-item.worklog`.
