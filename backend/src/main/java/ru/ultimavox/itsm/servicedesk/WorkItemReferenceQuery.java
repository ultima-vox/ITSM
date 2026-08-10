package ru.ultimavox.itsm.servicedesk;

import java.util.UUID;

/** Public Service Desk contract for cross-module opaque work-item references. */
public interface WorkItemReferenceQuery {
  boolean exists(UUID workItemId);
}
