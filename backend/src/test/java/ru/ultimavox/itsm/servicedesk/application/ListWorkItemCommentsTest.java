package ru.ultimavox.itsm.servicedesk.application;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;

@ExtendWith(MockitoExtension.class)
class ListWorkItemCommentsTest {
  @Mock WorkItemStore store;

  @Test
  void requesterQueryFailsClosedByExcludingInternalNotes() {
    UUID id = UUID.randomUUID();
    when(store.requireById(id)).thenReturn(item(id));
    when(store.listComments(id, false)).thenReturn(List.of());
    new ListWorkItemComments(store).list(id, false);
    verify(store).listComments(id, false);
  }

  private WorkItem item(UUID id) {
    Instant now = Instant.parse("2026-08-11T00:00:00Z");
    return new WorkItem(id, "INC-1", WorkItem.Type.INCIDENT, "title", "description", "service",
        WorkItem.State.IN_PROGRESS, WorkItem.Priority.MEDIUM, WorkItem.Impact.MEDIUM,
        WorkItem.Urgency.MEDIUM, "agent", "requester", "team", null, null, false,
        now, now, null);
  }
}
