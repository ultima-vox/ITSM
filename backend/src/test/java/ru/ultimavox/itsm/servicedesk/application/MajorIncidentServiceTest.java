package ru.ultimavox.itsm.servicedesk.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Impact;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Priority;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.State;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Type;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Urgency;

@ExtendWith(MockitoExtension.class)
class MajorIncidentServiceTest {

  @Mock WorkItemStore store;
  @Mock JdbcTemplate jdbc;
  @Mock AuditTrail audit;
  @Mock IntegrationEventOutbox outbox;
  private MajorIncidentService service;
  private final UUID id = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new MajorIncidentService(store, jdbc, audit, outbox);
  }

  @Test
  void rejectsServiceRequest() {
    when(store.requireById(id)).thenReturn(item(Type.SERVICE_REQUEST, State.NEW));
    assertThatThrownBy(() -> service.declare(id, "commander", "summary", "actor"))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("Only incidents");
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void rejectsTerminalIncident() {
    when(store.requireById(id)).thenReturn(item(Type.INCIDENT, State.CLOSED));
    assertThatThrownBy(() -> service.declare(id, "commander", "summary", "actor"))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("Closed incident");
  }

  @Test
  void rejectsBlankDeclarationFields() {
    when(store.requireById(id)).thenReturn(item(Type.INCIDENT, State.NEW));
    assertThatThrownBy(() -> service.declare(id, " ", "summary", "actor"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("commander");
    assertThatThrownBy(() -> service.declare(id, "commander", " ", "actor"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("summary");
  }

  @Test
  void declaresAndEmitsEvent() {
    when(store.requireById(id)).thenReturn(item(Type.INCIDENT, State.IN_PROGRESS));
    when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(), any()))
        .thenReturn(List.of());
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

    service.declare(id, " commander ", " outage ", "actor");

    verify(audit).append(any());
    verify(outbox).record(any());
  }

  private WorkItem item(Type type, State state) {
    Instant now = Instant.parse("2026-08-11T00:00:00Z");
    return new WorkItem(
        id, "INC-1", type, "title", "description", "service", state, Priority.MEDIUM,
        Impact.MEDIUM, Urgency.MEDIUM, "agent", "requester", "team", null, null,
        false, now, now, state == State.CLOSED ? now : null
    );
  }
}
