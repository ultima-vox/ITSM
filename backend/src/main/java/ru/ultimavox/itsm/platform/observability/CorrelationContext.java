package ru.ultimavox.itsm.platform.observability;

import java.util.Optional;
import java.util.UUID;

public final class CorrelationContext {
  private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

  private CorrelationContext() {}

  public static Optional<UUID> current() {
    return Optional.ofNullable(CURRENT.get());
  }

  public static UUID currentOrCreate() {
    return current().orElseGet(UUID::randomUUID);
  }

  static void set(UUID correlationId) {
    CURRENT.set(correlationId);
  }

  static void clear() {
    CURRENT.remove();
  }
}
