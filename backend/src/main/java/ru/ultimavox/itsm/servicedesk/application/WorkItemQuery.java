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
    return search(new Filter(null, null, null, null, null, subject, null), 0, 50).items();
  }

  public record Filter(
      State state,
      Type type,
      String assigneeId,
      Priority priority,
      String query,
      String requesterId,
      SortBy sort
  ) {}

  public record SortBy(String field, boolean desc) {
    private static final java.util.Set<String> ALLOWED = java.util.Set.of(
        "created_at", "updated_at", "number", "title", "priority", "state", "type"
    );
    public SortBy {
      if (field == null || !ALLOWED.contains(field)) field = "updated_at";
      if (desc == null) desc = true;
    }
  }

  public record PageResult(List<WorkItem> items, long total, int page, int size) {}
}
