package ru.ultimavox.itsm.servicedesk.application;

import java.util.UUID;

public class WorkItemNotFoundException extends RuntimeException {

  public WorkItemNotFoundException(UUID id) {
    super("Work item not found: " + id);
  }
}
