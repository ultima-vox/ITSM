package ru.ultimavox.itsm.servicedesk.application;

import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.ultimavox.itsm.servicedesk.WorkItemReferenceQuery;

/** Adapter stays inside Service Desk ownership and inherits organization-scoped lookup. */
@Component
class JdbcWorkItemReferenceQuery implements WorkItemReferenceQuery {
  private final WorkItemStore store;

  JdbcWorkItemReferenceQuery(WorkItemStore store) {
    this.store = store;
  }

  @Override
  public boolean exists(UUID workItemId) {
    return workItemId != null && store.findById(workItemId).isPresent();
  }
}
