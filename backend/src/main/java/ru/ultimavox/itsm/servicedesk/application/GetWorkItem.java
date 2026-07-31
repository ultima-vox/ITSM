package ru.ultimavox.itsm.servicedesk.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;

@Service
public class GetWorkItem {

  private final WorkItemStore store;

  GetWorkItem(WorkItemStore store) {
    this.store = store;
  }

  public WorkItem get(UUID id) {
    return store.requireById(id);
  }
}
