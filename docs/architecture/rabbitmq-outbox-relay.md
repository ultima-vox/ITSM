# RabbitMQ outbox relay

The `RabbitOutboxRelay` polls committed, unpublished rows and publishes their serialized event envelopes to the durable `itsm.events` topic exchange using the event type as routing key. The AMQP message ID equals the immutable outbox event ID. Consumers must deduplicate it because delivery is at least once.

A publish failure increments `attempts`, records a bounded diagnostic and a timestamped `next_attempt_at` so the row is retried after exponential backoff (`itsm.outbox.backoff-base` doubled per attempt, capped by `itsm.outbox.backoff-max`). Rows that exhaust `itsm.outbox.max-attempts` are quarantined (`quarantined_at` set) and stop being polled so a poison event can never stall the queue or retry forever; quarantined rows are retained for operator review and remain unpublished. A successful broker call marks the row published and clears the retry schedule.

A process failure between publish and marking published intentionally produces a duplicate, not lost data. Broker connectivity is a temporary dependency failure, not a reason to reject the already committed business mutation.
