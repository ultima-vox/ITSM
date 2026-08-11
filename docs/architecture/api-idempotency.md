# API idempotency

Critical create operations accept optional `Idempotency-Key`:

- `POST /api/v1/work-items`
- `POST /api/v1/catalog/items/{id}/requests`

Keys contain 1-128 URL-safe characters. Scope is trusted organization, authenticated
actor, and operation. First successful request stores request SHA-256 and exact JSON
response in same PostgreSQL transaction as domain write, audit entry, and outbox event.
Records become eligible for deletion after 24 hours; scheduled retention deletes expired
rows daily (`itsm.idempotency.retention-cron`).

Retry with same key and semantic payload returns original response and
`Idempotency-Replayed: true`. Reusing key with different payload returns `409`.
Failed transactions roll back reservation, so caller may retry. Frontend creates one
UUID key per submission; its automatic auth refresh retry reuses same request options
and key.
