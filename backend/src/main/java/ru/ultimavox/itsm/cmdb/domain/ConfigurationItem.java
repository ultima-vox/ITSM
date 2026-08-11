package ru.ultimavox.itsm.cmdb.domain;

import java.util.Map;
import java.util.UUID;

/** Typed CI with relationship semantics used by impact analysis. */
public record ConfigurationItem(
    UUID id,
    String name,
    String classKey,
    Status status,
    Map<String, Object> attributes,
    long version
) {
  public ConfigurationItem {
    attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
  }

  public enum Status { OPERATIONAL, DEGRADED, MAINTENANCE, RETIRED }
}
