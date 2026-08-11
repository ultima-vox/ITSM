package ru.ultimavox.itsm.servicedesk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Impact;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Priority;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.State;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Type;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Urgency;

@ExtendWith(MockitoExtension.class)
class WorkItemQueryTest {

  @Mock WorkItemStore store;

  private WorkItemQuery query;

  @BeforeEach
  void setUp() {
    query = new WorkItemQuery(store);
  }

  @Test
  void search_forwards_filters_and_clamps_page_size() {
    WorkItem item = sample();
    WorkItemQuery.Filter filter = new WorkItemQuery.Filter(
        State.NEW,
        Type.INCIDENT,
        "agent-1",
        Priority.CRITICAL,
        "VPN",
        "requester-1"
    );
    when(store.count(filter)).thenReturn(1L);
    when(store.search(eq(filter), eq(0), eq(200))).thenReturn(List.of(item));

    WorkItemQuery.PageResult page = query.search(filter, -1, 999);

    assertThat(page.total()).isEqualTo(1L);
    assertThat(page.page()).isEqualTo(0);
    assertThat(page.size()).isEqualTo(200);
    assertThat(page.items()).containsExactly(item);

    ArgumentCaptor<WorkItemQuery.Filter> filterCaptor = ArgumentCaptor.forClass(WorkItemQuery.Filter.class);
    verify(store).count(filterCaptor.capture());
    assertThat(filterCaptor.getValue().state()).isEqualTo(State.NEW);
    assertThat(filterCaptor.getValue().type()).isEqualTo(Type.INCIDENT);
    assertThat(filterCaptor.getValue().assigneeId()).isEqualTo("agent-1");
    assertThat(filterCaptor.getValue().priority()).isEqualTo(Priority.CRITICAL);
    assertThat(filterCaptor.getValue().query()).isEqualTo("VPN");
    assertThat(filterCaptor.getValue().requesterId()).isEqualTo("requester-1");
  }

  @Test
  void requesterConvenienceQueryAlwaysScopesBySubject() {
    WorkItemQuery.Filter expected = new WorkItemQuery.Filter(
        null, null, null, null, null, "requester-7");
    when(store.count(expected)).thenReturn(0L);
    when(store.search(expected, 0, 50)).thenReturn(List.of());
    query.findVisibleTo("requester-7");
    verify(store).count(expected);
    verify(store).search(expected, 0, 50);
  }

  private static WorkItem sample() {
    Instant now = Instant.parse("2026-07-30T10:00:00Z");
    return new WorkItem(
        UUID.randomUUID(),
        "INC-001842",
        Type.INCIDENT,
        "VPN unavailable",
        "Remote employees cannot connect",
        "Workplace",
        State.NEW,
        Priority.CRITICAL,
        Impact.HIGH,
        Urgency.HIGH,
        "agent-1",
        "user-42",
        "sd-l1",
        null,
        null,
        false,
        now,
        now,
        null
    );
  }
}
