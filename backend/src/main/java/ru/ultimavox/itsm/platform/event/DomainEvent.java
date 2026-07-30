package ru.ultimavox.itsm.platform.event;
import java.time.Instant; import java.util.Map; import java.util.UUID;
/** An immutable event envelope suitable for the transactional outbox and external transport. */
public record DomainEvent(UUID id, String type, int schemaVersion, Instant occurredAt, UUID correlationId, String aggregateType, String aggregateId, Map<String,Object> data) {}
