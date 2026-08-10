package ru.ultimavox.itsm.servicedesk.application;

import java.util.UUID;

public final class WorkItemConcurrencyException extends RuntimeException {
  public WorkItemConcurrencyException(UUID id, long version) {
    super("Work item %s changed after version %d; reload and retry".formatted(id, version));
  }
}
