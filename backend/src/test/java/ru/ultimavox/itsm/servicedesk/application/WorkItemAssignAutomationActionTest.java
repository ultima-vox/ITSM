package ru.ultimavox.itsm.servicedesk.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.ultimavox.itsm.platform.event.DomainEvent;

class WorkItemAssignAutomationActionTest {

  private final AssignWorkItem assignWorkItem = mock(AssignWorkItem.class);
  private final WorkItemAssignAutomationAction action =
      new WorkItemAssignAutomationAction(assignWorkItem);

  @Test
  void assignsWorkItemFromDataPlaceholder() {
    String id = UUID.randomUUID().toString();
    DomainEvent event = workItemEvent(id, "bob");

    action.execute(event, Map.of("assigneeId", "{{data.requesterId}}", "teamId", "helpdesk"));

    verify(assignWorkItem).assign(
        eq(UUID.fromString(id)), eq(new AssignWorkItem.Command("bob", "helpdesk")), eq("alice"));
  }

  @Test
  void assignsWithLiteralAssignee() {
    String id = UUID.randomUUID().toString();
    action.execute(workItemEvent(id, "bob"), Map.of("assigneeId", "support-l2"));

    verify(assignWorkItem).assign(
        eq(UUID.fromString(id)), eq(new AssignWorkItem.Command("support-l2", null)), eq("alice"));
  }

  @Test
  void rejectsMissingAssignee() {
    DomainEvent event = workItemEvent(UUID.randomUUID().toString(), "bob");

    assertThatThrownBy(() -> action.execute(event, Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("assigneeId");
    verify(assignWorkItem, never()).assign(any(), any(), any());
  }

  @Test
  void rejectsNonWorkItemAggregate() {
    DomainEvent event = new DomainEvent(
        UUID.randomUUID(), "asset.created", 1, Instant.now(), UUID.randomUUID(), null,
        "org-1", "alice", "asset", "asset-9", Map.of());

    assertThatThrownBy(() -> action.execute(event, Map.of("assigneeId", "support")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("work-item");
    verify(assignWorkItem, never()).assign(any(), any(), any());
  }

  private static DomainEvent workItemEvent(String id, String requesterId) {
    return new DomainEvent(
        UUID.randomUUID(), "incident.created", 1, Instant.now(), UUID.randomUUID(), null,
        "org-1", "alice", "work-item", id, Map.<String, Object>of("requesterId", requesterId));
  }
}
