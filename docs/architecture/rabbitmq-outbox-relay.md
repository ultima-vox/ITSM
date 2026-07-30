# RabbitMQ outbox relay

The `RabbitOutboxRelay` polls committed, unpublished rows and publishes their serialized event envelopes to the durable `itsm.events` topic exchange using the event type as routing key. The AMQP message ID equals the immutable outbox event ID. Consumers must deduplicate it because delivery is at least once.

A publish failure increments `attempts`, stores a bounded diagnostic and leaves `published_at` empty for retry. A successful broker call marks the row published. A process failure between publish and marking published intentionally produces a duplicate, not lost data. Broker connectivity is a temporary dependency failure, not a reason to reject the already committed business mutation.
