package ru.ultimavox.itsm.platform.event;

import java.time.Instant;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;
import ru.ultimavox.itsm.platform.observability.CorrelationContext;

/** Immutable, versioned integration-event envelope persisted by transactional outbox. */
public record DomainEvent(
    UUID id,
    String type,
    int schemaVersion,
    Instant occurredAt,
    UUID correlationId,
    UUID causationId,
    String organizationId,
    String actorId,
    String aggregateType,
    String aggregateId,
    Map<String, Object> data
) {
  public DomainEvent {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(occurredAt, "occurredAt");
    correlationId = correlationId == null ? CorrelationContext.currentOrCreate() : correlationId;
    organizationId = normalized(organizationId, EventContext.organizationId());
    actorId = normalized(actorId, EventContext.actorId());
    Objects.requireNonNull(aggregateType, "aggregateType");
    Objects.requireNonNull(aggregateId, "aggregateId");
    data = data == null
        ? Map.of()
        : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    if (type.isBlank() || aggregateType.isBlank() || aggregateId.isBlank()) {
      throw new IllegalArgumentException("event type and aggregate identity must not be blank");
    }
    if (schemaVersion < 1) {
      throw new IllegalArgumentException("schemaVersion must be positive");
    }
  }

  public DomainEvent(
      UUID id, String type, int schemaVersion, Instant occurredAt, UUID correlationId,
      String aggregateType, String aggregateId, Map<String, Object> data
  ) {
    this(id, type, schemaVersion, occurredAt, correlationId, null, null, null,
        aggregateType, aggregateId, data);
  }

  private static String normalized(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
