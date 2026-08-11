package ru.ultimavox.itsm.servicedesk.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Impact;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Priority;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.State;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Type;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Urgency;

@ExtendWith(MockitoExtension.class)
class SubmitWorkItemSurveyTest {
  @Mock WorkItemStore store;
  @Mock JdbcTemplate jdbc;
  @Mock AuditTrail audit;
  private SubmitWorkItemSurvey service;
  private final UUID id = UUID.randomUUID();

  @BeforeEach void setUp() { service = new SubmitWorkItemSurvey(store, jdbc, audit); }

  @Test void rejectsRatingOutsideFivePointScale() {
    assertThatThrownBy(() -> service.submit(id, 0, null, "requester"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("between 1 and 5");
    verify(store, never()).requireById(id);
  }

  @Test void rejectsNonRequester() {
    when(store.requireById(id)).thenReturn(item(State.RESOLVED));
    assertThatThrownBy(() -> service.submit(id, 5, null, "somebody-else"))
        .isInstanceOf(AccessDeniedException.class).hasMessageContaining("Only requester");
    verify(audit, never()).append(org.mockito.ArgumentMatchers.any());
  }

  @Test void rejectsSurveyBeforeResolution() {
    when(store.requireById(id)).thenReturn(item(State.IN_PROGRESS));
    assertThatThrownBy(() -> service.submit(id, 5, null, "requester"))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("resolved or closed");
    verify(audit, never()).append(org.mockito.ArgumentMatchers.any());
  }

  @Test void storesAndAuditsValidRequesterSurvey() {
    when(store.requireById(id)).thenReturn(item(State.RESOLVED));
    when(jdbc.update(anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

    var result = service.submit(id, 4, "  useful response  ", "requester");

    org.assertj.core.api.Assertions.assertThat(result.rating()).isEqualTo(4);
    org.assertj.core.api.Assertions.assertThat(result.comment()).isEqualTo("useful response");
    verify(audit).append(any());
  }

  private WorkItem item(State state) {
    Instant now = Instant.parse("2026-08-11T00:00:00Z");
    return new WorkItem(id, "INC-1", Type.INCIDENT, "title", "description", "service",
        state, Priority.MEDIUM, Impact.MEDIUM, Urgency.MEDIUM, "agent", "requester", "team",
        null, null, false, now, now, state == State.CLOSED ? now : null);
  }
}
