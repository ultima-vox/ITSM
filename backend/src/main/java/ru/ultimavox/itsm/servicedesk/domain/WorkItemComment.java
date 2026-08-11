package ru.ultimavox.itsm.servicedesk.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Operator or requester comment attached to a work item. */
public record WorkItemComment(
    UUID id,
    UUID workItemId,
    String authorId,
    String body,
    boolean internal,
    Instant createdAt
) {
  public WorkItemComment {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(workItemId, "workItemId");
    Objects.requireNonNull(authorId, "authorId");
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(createdAt, "createdAt");
    if (body.isBlank()) {
      throw new IllegalArgumentException("comment body must not be blank");
    }
  }
}
