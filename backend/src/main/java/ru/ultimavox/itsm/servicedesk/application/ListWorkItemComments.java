package ru.ultimavox.itsm.servicedesk.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.servicedesk.domain.WorkItemComment;

@Service
public class ListWorkItemComments {

  private final WorkItemStore store;

  ListWorkItemComments(WorkItemStore store) {
    this.store = store;
  }

  public List<WorkItemComment> list(UUID workItemId, boolean includeInternal) {
    store.requireById(workItemId);
    return store.listComments(workItemId, includeInternal);
  }
}
