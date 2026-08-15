package ru.ultimavox.itsm.servicedesk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.notification.NotificationRequest;
import ru.ultimavox.itsm.platform.notification.NotificationService;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Impact;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Priority;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.State;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Type;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Urgency;

class WorkItemSlaWarningNotifierTest {

  private final WorkItemStore store = mock(WorkItemStore.class);
  private final NotificationService notifications = mock(NotificationService.class);
  private final WorkItemSlaWarningNotifier notifier =
      new WorkItemSlaWarningNotifier(store, notifications);

  private final UUID id = UUID.fromString("b2ff9175-7a70-4d16-b60b-051deb0d2e01");

  @Test
  void notifies_assignee_on_warning() {
    when(store.requireById(id)).thenReturn(item("agent-9", "user-42"));

    notifier.execute(
        warningEvent(id.toString(), "2026-08-02T14:00:00Z"),
        Map.of("workItemId", "{{data.aggregateId}}"));

    ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
    verify(notifications).send(captor.capture());
    NotificationRequest sent = captor.getValue();
    assertThat(sent.templateKey()).isEqualTo("sla.warning");
    assertThat(sent.recipientSubject()).isEqualTo("agent-9");
    assertThat(sent.channel()).isEqualTo(NotificationRequest.Channel.IN_APP);
    assertThat(sent.variables()).containsEntry("workItemId", id.toString());
    assertThat(sent.variables()).containsEntry("number", "INC-001842");
    assertThat(sent.variables()).containsEntry("dueAt", "2026-08-02T14:00:00Z");
  }

  @Test
  void falls_back_to_requester_when_unassigned() {
    when(store.requireById(id)).thenReturn(item(null, "user-42"));

    notifier.execute(warningEvent(id.toString(), null), Map.of("workItemId", "{{data.aggregateId}}"));

    ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
    verify(notifications).send(captor.capture());
    assertThat(captor.getValue().recipientSubject()).isEqualTo("user-42");
  }

  @Test
  void skips_when_item_has_no_owner() {
    when(store.requireById(id)).thenReturn(item(null, ""));

    notifier.execute(warningEvent(id.toString(), null), Map.of());

    verify(notifications, never()).send(any());
  }

  @Test
  void rejects_non_warning_event() {
    DomainEvent event = new DomainEvent(
        UUID.randomUUID(), "sla.breached", 1, Instant.now(), UUID.randomUUID(), null,
        "org-1", "system", "sla-clock", id.toString(),
        Map.of("aggregateId", id.toString()));

    assertThatThrownBy(() -> notifier.execute(event, Map.of("workItemId", "{{data.aggregateId}}")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sla.warning");
    verify(notifications, never()).send(any());
  }

  @Test
  void rejects_missing_work_item_id() {
    DomainEvent event = new DomainEvent(
        UUID.randomUUID(), "sla.warning", 1, Instant.now(), UUID.randomUUID(), null,
        "org-1", "system", "sla-clock", id.toString(), Map.of());

    assertThatThrownBy(() -> notifier.execute(event, Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("workItemId");
    verify(notifications, never()).send(any());
  }

  private static DomainEvent warningEvent(String aggregateId, String dueAt) {
    Map<String, Object> data = new java.util.HashMap<>();
    data.put("aggregateId", aggregateId);
    if (dueAt != null) {
      data.put("dueAt", dueAt);
    }
    return new DomainEvent(
        UUID.randomUUID(), "sla.warning", 1, Instant.now(), UUID.randomUUID(), null,
        "org-1", "system", "sla-clock", aggregateId, data);
  }

  private static WorkItem item(String assignee, String requester) {
    Instant now = Instant.parse("2026-07-30T12:00:00Z");
    return new WorkItem(
        UUID.fromString("b2ff9175-7a70-4d16-b60b-051deb0d2e01"),
        "INC-001842",
        Type.INCIDENT,
        "VPN unavailable",
        "Remote employees cannot connect",
        "Workplace",
        State.IN_PROGRESS,
        Priority.CRITICAL,
        Impact.HIGH,
        Urgency.HIGH,
        assignee,
        requester,
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
