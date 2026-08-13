package ru.ultimavox.itsm.platform.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.notification.NotificationService;
import ru.ultimavox.itsm.platform.search.SearchIndexService;

class AllowlistedActionExecutorTest {

  private final NotificationService notifications = mock(NotificationService.class);
  private final SearchIndexService searchIndex = mock(SearchIndexService.class);

  private final DomainEvent event = new DomainEvent(
      UUID.randomUUID(), "incident.created", 1, Instant.now(), UUID.randomUUID(), null,
      "org-1", "alice", "work-item", "11111111-1111-1111-1111-111111111111", Map.of());

  @Test
  void delegatesRegisteredHandlerActions() {
    AutomationActionHandler handler = mock(AutomationActionHandler.class);
    when(handler.actionType()).thenReturn("assign");
    AllowlistedActionExecutor executor =
        new AllowlistedActionExecutor(notifications, searchIndex, List.of(handler));

    AutomationRule.Action action = new AutomationRule.Action("assign", Map.<String, Object>of("assigneeId", "bob"));

    assertThat(executor.supports("assign")).isTrue();
    executor.execute(action, event);
    verify(handler).execute(event, action.parameters());
  }

  @Test
  void rejectsUnknownActionTypes() {
    AllowlistedActionExecutor executor = new AllowlistedActionExecutor(notifications, searchIndex, List.of());

    assertThat(executor.supports("notify")).isTrue();
    assertThat(executor.supports("assign")).isFalse();
    assertThatThrownBy(() -> executor.execute(new AutomationRule.Action("assign", Map.of()), event))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not allowlisted");
  }

  @Test
  void rejectsDuplicateHandlerTypesAtWiringTime() {
    AutomationActionHandler first = mock(AutomationActionHandler.class);
    when(first.actionType()).thenReturn("assign");
    AutomationActionHandler second = mock(AutomationActionHandler.class);
    when(second.actionType()).thenReturn("assign");

    assertThatThrownBy(() -> new AllowlistedActionExecutor(notifications, searchIndex, List.of(first, second)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate");
  }

  @Test
  void rejectsHandlerClashingWithBuiltinType() {
    AutomationActionHandler handler = mock(AutomationActionHandler.class);
    when(handler.actionType()).thenReturn("log");

    assertThatThrownBy(() -> new AllowlistedActionExecutor(notifications, searchIndex, List.of(handler)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("built-in");
  }

  @Test
  void builtinLogActionDoesNotTouchRegisteredHandlers() {
    AutomationActionHandler handler = mock(AutomationActionHandler.class);
    when(handler.actionType()).thenReturn("assign");
    AllowlistedActionExecutor executor =
        new AllowlistedActionExecutor(notifications, searchIndex, List.of(handler));

    executor.execute(new AutomationRule.Action("log", Map.of()), event);

    verify(handler, never()).execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }
}
