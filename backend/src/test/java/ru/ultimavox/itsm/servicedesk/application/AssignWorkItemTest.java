package ru.ultimavox.itsm.servicedesk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.notification.NotificationRequest;
import ru.ultimavox.itsm.platform.notification.NotificationService;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Impact;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Priority;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.State;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Type;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Urgency;

@ExtendWith(MockitoExtension.class)
class AssignWorkItemTest {

  @Mock WorkItemStore store;
  @Mock AuditTrail audit;
  @Mock IntegrationEventOutbox outbox;
  @Mock NotificationService notifications;

  private AssignWorkItem service;
  private final UUID id = UUID.fromString("a1ff9175-7a70-4d16-b60b-051deb0d2e99");
  private final Instant now = Instant.parse("2026-07-30T12:00:00Z");

  @BeforeEach
  void setUp() {
    service = new AssignWorkItem(store, audit, outbox, notifications);
  }

  @Test
  void assign_notifies_new_assignee() {
    when(store.requireById(id)).thenReturn(openItem(null));

    WorkItem result = service.assign(
        id,
        new AssignWorkItem.Command("agent-7", "sd-l1"),
        "dispatcher-1"
    );

    assertThat(result.assigneeId()).isEqualTo("agent-7");
    verify(store).update(any());
    verify(audit).append(any());
    verify(outbox).record(any());

    ArgumentCaptor<NotificationRequest> cap = ArgumentCaptor.forClass(NotificationRequest.class);
    verify(notifications).send(cap.capture());
    assertThat(cap.getValue().templateKey()).isEqualTo("work-item.assigned");
    assertThat(cap.getValue().recipientSubject()).isEqualTo("agent-7");
    assertThat(cap.getValue().channel()).isEqualTo(NotificationRequest.Channel.IN_APP);
    assertThat(cap.getValue().variables()).containsEntry("number", "INC-000100");
  }

  @Test
  void closed_item_is_rejected() {
    WorkItem closed = openItem("agent-1")
        .transition(State.IN_PROGRESS, null, null, now)
        .transition(State.RESOLVED, "FIXED", "done", now)
        .transition(State.CLOSED, "FIXED", "done", now);
    when(store.requireById(id)).thenReturn(closed);

    assertThatThrownBy(() -> service.assign(
        id,
        new AssignWorkItem.Command("agent-2", null),
        "dispatcher-1"
    )).isInstanceOf(IllegalStateException.class);

    verify(store, never()).update(any());
    verify(notifications, never()).send(any());
  }

  private WorkItem openItem(String assigneeId) {
    return new WorkItem(
        id,
        "INC-000100",
        Type.INCIDENT,
        "Printer jam",
        "Floor 3",
        "Workplace",
        State.NEW,
        Priority.MEDIUM,
        Impact.MEDIUM,
        Urgency.MEDIUM,
        assigneeId,
        "user-1",
        "sd-l1",
        null,
        null,
        now,
        now,
        null
    );
  }
}
