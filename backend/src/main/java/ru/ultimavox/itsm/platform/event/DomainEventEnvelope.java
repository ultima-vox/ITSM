package ru.ultimavox.itsm.platform.event;

import java.util.Objects;

/**
 * In-process event delivery envelope. Wraps a {@link DomainEvent} with the automation
 * execution depth at which the event was produced, enabling bounded automation cascades.
 * Depth 0 means the event originated from user/system action; depth &ge; 1 means it was
 * produced by an automation action. This envelope is not persisted; the outbox row is the
 * authoritative record.
 */
public record DomainEventEnvelope(DomainEvent event, int automationDepth) {

  public DomainEventEnvelope {
    Objects.requireNonNull(event, "event");
    if (automationDepth < 0) {
      throw new IllegalArgumentException("automationDepth must not be negative");
    }
  }
}
