package ru.ultimavox.itsm.servicedesk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.notification.NotificationRequest;
import ru.ultimavox.itsm.platform.notification.NotificationService;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.platform.workflow.WorkflowDefinition;
import ru.ultimavox.itsm.platform.workflow.WorkflowDefinition.Transition;
import ru.ultimavox.itsm.platform.workflow.WorkflowEngine;
import ru.ultimavox.itsm.platform.workflow.WorkflowEngine.TransitionCommand;
import ru.ultimavox.itsm.platform.workflow.WorkflowInstance;
import ru.ultimavox.itsm.platform.workflow.WorkflowTransitionException;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Impact;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Priority;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.State;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Type;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Urgency;

@ExtendWith(MockitoExtension.class)
class TransitionWorkItemTest {

  @Mock WorkItemStore store;
  @Mock AuditTrail audit;
  @Mock IntegrationEventOutbox outbox;
  @Mock ObjectProvider<WorkflowEngine> workflowEngineProvider;
  @Mock WorkflowEngine workflowEngine;
  @Mock NotificationService notifications;
  @Mock WorkItemSearchIndexer searchIndexer;

  private TransitionWorkItem service;
  private final UUID id = UUID.fromString("b2ff9175-7a70-4d16-b60b-051deb0d2e01");
  private final Instant now = Instant.parse("2026-07-30T12:00:00Z");

  @BeforeEach
  void setUp() {
    when(workflowEngineProvider.getIfAvailable()).thenReturn(null);
    service = new TransitionWorkItem(
        store, audit, outbox, workflowEngineProvider, notifications, searchIndexer
    );
  }

  @Test
  void legal_transition_persists_and_audits() {
    when(store.requireById(id)).thenReturn(item(State.NEW));

    WorkItem result = service.transition(
        id,
        new TransitionWorkItem.Command(State.IN_PROGRESS, null, null),
        "agent-1"
    );

    assertThat(result.state()).isEqualTo(State.IN_PROGRESS);
    ArgumentCaptor<WorkItem> captor = ArgumentCaptor.forClass(WorkItem.class);
    verify(store).update(captor.capture());
    assertThat(captor.getValue().state()).isEqualTo(State.IN_PROGRESS);
    verify(audit).append(any());
    verify(outbox).record(any());
    verify(searchIndexer).index(any(WorkItem.class));

    ArgumentCaptor<NotificationRequest> notif = ArgumentCaptor.forClass(NotificationRequest.class);
    verify(notifications).send(notif.capture());
    assertThat(notif.getValue().templateKey()).isEqualTo("work-item.transitioned");
    assertThat(notif.getValue().recipientSubject()).isEqualTo("agent-9");
    assertThat(notif.getValue().channel()).isEqualTo(NotificationRequest.Channel.IN_APP);
  }

  @Test
  void illegal_path_is_rejected_without_update() {
    when(store.requireById(id)).thenReturn(item(State.NEW));

    assertThatThrownBy(() -> service.transition(
        id,
        new TransitionWorkItem.Command(State.CLOSED, "X", "Y"),
        "agent-1"
    )).isInstanceOf(IllegalStateException.class);

    verify(store, never()).update(any());
    verify(notifications, never()).send(any());
  }

  @Test
  void platform_workflow_used_when_matching_transition_exists() {
    when(workflowEngineProvider.getIfAvailable()).thenReturn(workflowEngine);
    when(store.requireById(id)).thenReturn(item(State.NEW));

    WorkflowDefinition definition = new WorkflowDefinition(
        UUID.randomUUID(),
        "work-item",
        1,
        "NEW",
        Set.of("NEW", "IN_PROGRESS", "RESOLVED", "CLOSED"),
        List.of(new Transition(
            "start", "NEW", "IN_PROGRESS",
            Set.of("work-item.transition"),
            Set.of()
        ))
    );
    when(workflowEngine.loadDefinition("work-item")).thenReturn(Optional.of(definition));
    when(workflowEngine.applyTransition(any(TransitionCommand.class)))
        .thenReturn(new WorkflowInstance(
            UUID.randomUUID(), "work-item", id.toString(),
            "IN_PROGRESS", 1, 2, Instant.now()
        ));

    WorkItem result = service.transition(
        id,
        new TransitionWorkItem.Command(State.IN_PROGRESS, null, null),
        "agent-1"
    );

    assertThat(result.state()).isEqualTo(State.IN_PROGRESS);
    ArgumentCaptor<TransitionCommand> cmd = ArgumentCaptor.forClass(TransitionCommand.class);
    verify(workflowEngine).applyTransition(cmd.capture());
    assertThat(cmd.getValue().transitionKey()).isEqualTo("start");
    assertThat(cmd.getValue().objectId()).isEqualTo(id.toString());
    verify(store).update(any());
  }

  @Test
  void platform_workflow_rejection_is_not_bypassed() {
    when(workflowEngineProvider.getIfAvailable()).thenReturn(workflowEngine);
    when(store.requireById(id)).thenReturn(item(State.NEW));

    WorkflowDefinition definition = new WorkflowDefinition(
        UUID.randomUUID(),
        "work-item",
        1,
        "NEW",
        Set.of("NEW", "IN_PROGRESS"),
        List.of(new Transition(
            "start", "NEW", "IN_PROGRESS",
            Set.of("work-item.transition"),
            Set.of("assignee_id")
        ))
    );
    when(workflowEngine.loadDefinition("work-item")).thenReturn(Optional.of(definition));
    when(workflowEngine.applyTransition(any(TransitionCommand.class)))
        .thenThrow(new WorkflowTransitionException("Required field 'assignee_id' is missing"));

    assertThatThrownBy(() -> service.transition(
        id,
        new TransitionWorkItem.Command(State.IN_PROGRESS, null, null),
        "agent-1"
    )).isInstanceOf(WorkflowTransitionException.class);

    verify(store, never()).update(any());
  }

  @Test
  void falls_back_to_local_when_no_matching_workflow_edge() {
    when(workflowEngineProvider.getIfAvailable()).thenReturn(workflowEngine);
    when(store.requireById(id)).thenReturn(item(State.NEW));

    WorkflowDefinition definition = new WorkflowDefinition(
        UUID.randomUUID(),
        "work-item",
        1,
        "NEW",
        Set.of("NEW", "IN_PROGRESS", "CANCELLED"),
        List.of(new Transition(
            "cancel-new", "NEW", "CANCELLED",
            Set.of(),
            Set.of()
        ))
    );
    when(workflowEngine.loadDefinition("work-item")).thenReturn(Optional.of(definition));

    WorkItem result = service.transition(
        id,
        new TransitionWorkItem.Command(State.IN_PROGRESS, null, null),
        "agent-1"
    );

    assertThat(result.state()).isEqualTo(State.IN_PROGRESS);
    verify(workflowEngine, never()).applyTransition(any());
    verify(store).update(any());
  }

  private WorkItem item(State state) {
    return new WorkItem(
        id,
        "INC-001842",
        Type.INCIDENT,
        "VPN unavailable",
        "Remote employees cannot connect",
        "Workplace",
        state,
        Priority.CRITICAL,
        Impact.HIGH,
        Urgency.HIGH,
        "agent-9",
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
