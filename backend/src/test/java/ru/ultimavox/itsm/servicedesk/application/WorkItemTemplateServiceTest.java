package ru.ultimavox.itsm.servicedesk.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;

@ExtendWith(MockitoExtension.class)
class WorkItemTemplateServiceTest {
  @Mock JdbcTemplate jdbc;
  @Mock AuditTrail audit;
  @Mock IntegrationEventOutbox outbox;
  private WorkItemTemplateService service;

  @BeforeEach
  void setUp() { service = new WorkItemTemplateService(jdbc, audit, outbox); }

  @Test
  void rejectsIncompleteTemplate() {
    var invalid = new WorkItemTemplateService.Command(
        "", WorkItem.Type.INCIDENT, "title", "description", "service",
        WorkItem.Impact.HIGH, WorkItem.Urgency.HIGH, null);
    assertThatThrownBy(() -> service.create(invalid, "actor"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("required");
    verify(jdbc, never()).update(anyString(), any(Object[].class));
  }

  @Test
  void createsAuditedTemplateAndEmitsEvent() {
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    service.create(new WorkItemTemplateService.Command(
        " Outage ", WorkItem.Type.INCIDENT, " Network outage ", " Investigate ",
        " Network ", WorkItem.Impact.HIGH, WorkItem.Urgency.HIGH, " team-net "), "actor");
    verify(audit).append(any());
    verify(outbox).record(any());
  }

  @Test
  void failsClosedOnStaleArchive() {
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
    assertThatThrownBy(() -> service.archive(java.util.UUID.randomUUID(), 3, "actor"))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("concurrently");
    verify(audit, never()).append(any());
  }

  @Test
  void updatesTemplateWithOptimisticVersionAndAudit() {
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    service.update(java.util.UUID.randomUUID(), 2, new WorkItemTemplateService.Command(
        "Outage", WorkItem.Type.INCIDENT, "Network outage", "Investigate",
        "Network", WorkItem.Impact.HIGH, WorkItem.Urgency.MEDIUM, "team-net"), "actor");
    verify(audit).append(any());
    verify(outbox).record(any());
  }
}
