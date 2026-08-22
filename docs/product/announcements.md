# Service announcements

During a major outage the same sentence gets retyped into every ticket. An announcement says it
once, to everyone it concerns, with a start and an end.

## Model

`announcement` (migration `V81`):

| Column | Meaning |
| --- | --- |
| `severity` | `INFO`, `WARNING`, `CRITICAL` — drives the banner styling and the ordering |
| `audience` | `ALL`, `AGENTS`, `REQUESTERS` |
| `starts_at` / `ends_at` | the window; a null end means it stays up until someone ends it |
| `published` | an unpublished row is a draft, invisible to the banner |
| `dismissible` | a critical broadcast can be made undismissable |
| `link_url` | optional "details" link |
| `version` | optimistic lock; a stale edit answers `409 Conflict` |

`GET /api/v1/announcements/active` returns published rows whose window contains *now* and whose
audience includes the caller, critical first. The caller's audience is derived from their grants:
an operator who may read work items is an agent, anyone else is a requester.

## Ending versus deleting

`POST /api/v1/announcements/{id}/retire` stamps `ends_at = now`, so the broadcast disappears from
the banner while the record of what was said, and when, survives. `DELETE` removes it outright —
use it for a draft, not for something people acted on.

## API

| Method | Path | Permission |
| --- | --- | --- |
| `GET` | `/api/v1/announcements/active` | `announcement.read` |
| `GET` | `/api/v1/announcements` | `announcement.admin` |
| `POST` | `/api/v1/announcements` | `announcement.admin` |
| `PATCH` | `/api/v1/announcements/{id}` | `announcement.admin` |
| `POST` | `/api/v1/announcements/{id}/retire` | `announcement.admin` |
| `DELETE` | `/api/v1/announcements/{id}` | `announcement.admin` |

`announcement.read` is granted to every service desk role including `REQUESTER`;
`announcement.admin` to `ADMIN` and `SERVICE_DESK_MANAGER`.

## Screens

The app shell renders active announcements above the page content. A dismissal is remembered per
browser in `localStorage`, so it does not need a server round trip and does not hide the banner
from anyone else. `/admin/announcements` is the editor: severity, audience, window, publish and
dismissible flags, plus **end now** for anything currently live.
