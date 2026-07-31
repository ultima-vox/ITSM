package ru.ultimavox.itsm.servicedesk.domain;

import java.time.Instant;
import java.util.UUID;

public record WorkItemLink(
    UUID id,
    UUID sourceId,
    UUID targetId,
    Type linkType,
    String createdBy,
    Instant createdAt
) {
  public enum Type {
    RELATED,
    DUPLICATE_OF,
    CAUSED_BY,
    CHILD_OF
  }
}
