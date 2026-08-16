# Notifications

PostgreSQL is the source of truth for in-app notifications.

## Schema

Table `notification` (Flyway `V18__notifications.sql`):

- recipient-scoped rows (`recipient_subject` = OIDC subject / principal name)
- `variables` JSONB for template payload (work item id, number, title, …)
- `read_at` null ⇒ unread
- optional `dedupe_key` unique per recipient (template + entity) to avoid double-delivery
- `source`, `entity_type`, `entity_id` for deep links and filtering

## API

| Method | Path | Notes |
|--------|------|--------|
| `GET` | `/api/v1/notifications?limit&offset&unreadOnly` | Actor-only list + `unreadCount` |
| `POST` | `/api/v1/notifications/{id}/read` | Mark one read (404 if not owner) |
| `POST` | `/api/v1/notifications/read-all` | Mark all read for actor |

## Delivery

`LoggingNotificationService` logs delivery intent and persists via `JdbcNotificationStore`.
EMAIL / WEBHOOK channels are recorded the same way; optional HTTP fan-out:

| Property | Default | Meaning |
|----------|---------|---------|
| `itsm.notifications.webhook.url` | _(empty)_ | When set, EMAIL/WEBHOOK channels POST JSON to this URL |
| `itsm.notifications.webhook.timeout` | `PT3S` | HTTP client timeout |

IN_APP stays in PostgreSQL only (no webhook by default).

## Retention

| Property | Default | Meaning |
|----------|---------|---------|
| `itsm.notifications.retention-days` | `90` | Delete rows older than N days (`0` = no purge) |
| `itsm.notifications.retention-cron` | `0 30 3 * * *` | Daily 03:30 |

## Dev note

`InMemoryNotificationStore` is **not** a Spring bean. Unit tests construct it directly. Production always uses JDBC.
