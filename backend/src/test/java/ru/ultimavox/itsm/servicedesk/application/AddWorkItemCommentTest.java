package ru.ultimavox.itsm.servicedesk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.notification.NotificationService;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;

@ExtendWith(MockitoExtension.class)
class AddWorkItemCommentTest {
  @Mock WorkItemStore store;
  @Mock AuditTrail audit;
  @Mock IntegrationEventOutbox outbox;
  @Mock NotificationService notifications;
  private AddWorkItemComment service;
  private final UUID id = UUID.randomUUID();

  @BeforeEach
  void setUp() { service = new AddWorkItemComment(store, audit, outbox, notifications); }

  @Test
  void internalNoteNeverNotifiesPotentialRequesterWatchers() {
    when(store.requireById(id)).thenReturn(item());
    var note = service.add(id, new AddWorkItemComment.Command(" secret diagnosis ", true), "agent");
    assertThat(note.internal()).isTrue();
    assertThat(note.body()).isEqualTo("secret diagnosis");
    verify(store).insertComment(note);
    verify(notifications, never()).send(any());
  }

  @Test
  void publicReplyKeepsPublicVisibility() {
    when(store.requireById(id)).thenReturn(item());
    var reply = service.add(id, new AddWorkItemComment.Command(" response ", false), "agent");
    assertThat(reply.internal()).isFalse();
    verify(audit).append(any());
    verify(outbox).record(any());
  }

  private WorkItem item() {
    Instant now = Instant.parse("2026-08-11T00:00:00Z");
    return new WorkItem(id, "INC-1", WorkItem.Type.INCIDENT, "title", "description", "service",
        WorkItem.State.IN_PROGRESS, WorkItem.Priority.MEDIUM, WorkItem.Impact.MEDIUM,
        WorkItem.Urgency.MEDIUM, "agent", "requester", "team", null, null, false,
        now, now, null);
  }
}
