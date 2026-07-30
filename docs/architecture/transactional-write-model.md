# Transactional write model

`CreateWorkItem` is the reference mutation flow. It validates the API command, obtains the authenticated subject, writes the module-owned aggregate, appends an audit record and records an immutable integration event in one PostgreSQL transaction. A future outbox relay owns RabbitMQ publishing, retry/backoff, duplicate-safe publisher confirms and the `published_at` update.

This avoids dual-write loss: no event can describe a mutation that was rolled back, and a committed mutation remains publishable after transient broker failure. Consumers must deduplicate by event ID and treat delivery as at-least-once.
