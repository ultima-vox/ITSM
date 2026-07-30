package ru.ultimavox.itsm.platform.outbox;
import ru.ultimavox.itsm.platform.event.DomainEvent;
/** Transactional recording port. A separate relay publishes records to RabbitMQ with retries and idempotency. */
public interface IntegrationEventOutbox { void record(DomainEvent event); }
