package ru.ultimavox.itsm.servicedesk.application;

import java.util.List;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Priority;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.State;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Type;

@Service
public class WorkItemQuery {

  private final WorkItemStore store;

  WorkItemQuery(WorkItemStore store) {
    this.store = store;
  }

  public PageResult search(Filter filter, int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 200);
    long total = store.count(filter);
    List<WorkItem> items = store.search(filter, safePage, safeSize);
    return new PageResult(items, total, safePage, safeSize);
  }

  /** Legacy convenience used by early list endpoint. */
  public List<WorkItem> findVisibleTo(String subject) {
    return search(new Filter(null, null, null, null, null, subject), 0, 50).items();
  }

  public record Filter(
      State state,
      Type type,
      String assigneeId,
      Priority priority,
      String query,
      String requesterId
  ) {}

  public record PageResult(List<WorkItem> items, long total, int page, int size) {}
}
