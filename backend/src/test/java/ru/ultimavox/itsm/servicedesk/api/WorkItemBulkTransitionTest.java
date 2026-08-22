package ru.ultimavox.itsm.servicedesk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.authorization.FieldAccessControl;
import ru.ultimavox.itsm.platform.idempotency.ApiIdempotencyService;
import ru.ultimavox.itsm.platform.workflow.WorkflowEngine;
import ru.ultimavox.itsm.platform.workflow.WorkflowTransitionException;
import ru.ultimavox.itsm.servicedesk.application.AddWorkItemComment;
import ru.ultimavox.itsm.servicedesk.application.AssignWorkItem;
import ru.ultimavox.itsm.servicedesk.application.BulkWorkItemService;
import ru.ultimavox.itsm.servicedesk.application.CreateWorkItem;
import ru.ultimavox.itsm.servicedesk.application.DuplicateWorkItemQuery;
import ru.ultimavox.itsm.servicedesk.application.EscalateWorkItem;
import ru.ultimavox.itsm.servicedesk.application.GetWorkItem;
import ru.ultimavox.itsm.servicedesk.application.ListWorkItemComments;
import ru.ultimavox.itsm.servicedesk.application.MajorIncidentService;
import ru.ultimavox.itsm.servicedesk.application.SubmitWorkItemSurvey;
import ru.ultimavox.itsm.servicedesk.application.TransitionWorkItem;
import ru.ultimavox.itsm.servicedesk.application.UpdateWorkItem;
import ru.ultimavox.itsm.servicedesk.application.WorkItemActivityQuery;
import ru.ultimavox.itsm.servicedesk.application.WorkItemAttachmentService;
import ru.ultimavox.itsm.servicedesk.application.WorkItemCiLinkService;
import ru.ultimavox.itsm.servicedesk.application.WorkItemLinkService;
import ru.ultimavox.itsm.servicedesk.application.WorkItemNotFoundException;
import ru.ultimavox.itsm.servicedesk.application.WorkItemQuery;
import ru.ultimavox.itsm.servicedesk.application.WorkItemSlaStateResolver;
import ru.ultimavox.itsm.servicedesk.application.WorkItemStatsQuery;
import ru.ultimavox.itsm.servicedesk.application.WorkItemWatcherService;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Impact;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Priority;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.State;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Type;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Urgency;

class WorkItemBulkTransitionTest {

  @Test
  void reportsPerItemFailuresAndChecksRecordPermission() {
    TransitionWorkItem transition = mock(TransitionWorkItem.class);
    AccessControl access = mock(AccessControl.class);
    Authentication auth = mock(Authentication.class);
    when(auth.getName()).thenReturn("operator");

    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    when(transition.transition(eq(first), any(), eq("operator")))
        .thenReturn(item(first, State.IN_PROGRESS));
    when(transition.transition(eq(second), any(), eq("operator")))
        .thenThrow(new IllegalStateException("Transition NEW -> RESOLVED is not allowed"));

    var response = controller(transition, access).bulkTransition(
        new WorkItemController.BulkTransitionRequest(
            List.of(first, second), State.IN_PROGRESS, null, null),
        auth
    );

    assertThat(response.succeeded()).isEqualTo(1);
    assertThat(response.results()).extracting(WorkItemController.BulkTransitionResult::id)
        .containsExactly(first, second);
    assertThat(response.results()).extracting(WorkItemController.BulkTransitionResult::success)
        .containsExactly(true, false);
    assertThat(response.results().get(0).status()).isEqualTo("IN_PROGRESS");
    assertThat(response.results().get(1).errorCode()).isEqualTo("INVALID_TRANSITION");
    verify(access).require("operator", "work-item.transition", "work-item", first.toString());
    verify(access).require("operator", "work-item.transition", "work-item", second.toString());
  }

  @Test
  void missingRecordIsPerItemNotFound() {
    TransitionWorkItem transition = mock(TransitionWorkItem.class);
    AccessControl access = mock(AccessControl.class);
    Authentication auth = mock(Authentication.class);
    when(auth.getName()).thenReturn("operator");
    UUID missing = UUID.randomUUID();
    UUID ok = UUID.randomUUID();
    when(transition.transition(eq(missing), any(), eq("operator")))
        .thenThrow(new WorkItemNotFoundException(missing));
    when(transition.transition(eq(ok), any(), eq("operator")))
        .thenReturn(item(ok, State.IN_PROGRESS));

    var response = controller(transition, access).bulkTransition(
        new WorkItemController.BulkTransitionRequest(
            List.of(missing, ok), State.IN_PROGRESS, null, null),
        auth
    );

    assertThat(response.succeeded()).isEqualTo(1);
    assertThat(response.results().get(0).errorCode()).isEqualTo("NOT_FOUND");
    assertThat(response.results().get(1).success()).isTrue();
  }

  @Test
  void workflowRejectionIsInvalidTransition() {
    TransitionWorkItem transition = mock(TransitionWorkItem.class);
    AccessControl access = mock(AccessControl.class);
    Authentication auth = mock(Authentication.class);
    when(auth.getName()).thenReturn("operator");
    UUID id = UUID.randomUUID();
    when(transition.transition(eq(id), any(), eq("operator")))
        .thenThrow(new WorkflowTransitionException("no edge"));

    var response = controller(transition, access).bulkTransition(
        new WorkItemController.BulkTransitionRequest(
            List.of(id), State.RESOLVED, "FIXED", "done"),
        auth
    );

    assertThat(response.succeeded()).isZero();
    assertThat(response.results()).singleElement()
        .extracting(WorkItemController.BulkTransitionResult::errorCode)
        .isEqualTo("INVALID_TRANSITION");
  }

  @Test
  void authorizationFailureRejectsRequest() {
    TransitionWorkItem transition = mock(TransitionWorkItem.class);
    AccessControl access = mock(AccessControl.class);
    Authentication auth = mock(Authentication.class);
    when(auth.getName()).thenReturn("operator");
    UUID first = UUID.randomUUID();
    UUID denied = UUID.randomUUID();
    doThrow(new AccessDeniedException("Permission denied: work-item.transition"))
        .when(access).require("operator", "work-item.transition", "work-item", denied.toString());

    assertThatThrownBy(() -> controller(transition, access).bulkTransition(
        new WorkItemController.BulkTransitionRequest(
            List.of(first, denied), State.IN_PROGRESS, null, null),
        auth
    )).isInstanceOf(AccessDeniedException.class);

    verify(transition, never()).transition(any(), any(), any());
  }

  private static WorkItemController controller(TransitionWorkItem transition, AccessControl access) {
    return new WorkItemController(
        mock(CreateWorkItem.class),
        mock(WorkItemQuery.class),
        mock(GetWorkItem.class),
        mock(UpdateWorkItem.class),
        mock(AssignWorkItem.class),
        mock(EscalateWorkItem.class),
        transition,
        mock(AddWorkItemComment.class),
        mock(ListWorkItemComments.class),
        mock(WorkItemActivityQuery.class),
        mock(WorkItemStatsQuery.class),
        mock(WorkItemAttachmentService.class),
        mock(WorkItemWatcherService.class),
        mock(WorkItemLinkService.class),
        mock(WorkItemCiLinkService.class),
        mock(SubmitWorkItemSurvey.class),
        mock(DuplicateWorkItemQuery.class),
        mock(MajorIncidentService.class),
        mock(BulkWorkItemService.class),
        access,
        mock(ApiIdempotencyService.class),
        mock(FieldAccessControl.class),
        mock(WorkItemSlaStateResolver.class),
        mock(WorkflowEngine.class)
    );
  }

  private static WorkItem item(UUID id, State state) {
    Instant now = Instant.parse("2026-07-30T12:00:00Z");
    return new WorkItem(
        id,
        "INC-000100",
        Type.INCIDENT,
        "Printer jam",
        "Floor 3",
        "Workplace",
        state,
        Priority.MEDIUM,
        Impact.MEDIUM,
        Urgency.MEDIUM,
        "agent-1",
        "user-1",
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
