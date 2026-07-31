package ru.ultimavox.itsm.servicedesk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.platform.sla.SlaDeadlineCalculator;
import ru.ultimavox.itsm.platform.workflow.WorkflowEngine;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Impact;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Priority;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Type;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Urgency;

@ExtendWith(MockitoExtension.class)
class CreateWorkItemTest {

  @Mock JdbcTemplate jdbc;
  @Mock WorkItemStore store;
  @Mock AuditTrail audit;
  @Mock IntegrationEventOutbox outbox;
  @Mock ObjectProvider<SlaDeadlineCalculator> slaCalculator;
  @Mock ObjectProvider<WorkflowEngine> workflowEngine;
  @Mock WorkItemSearchIndexer searchIndexer;
  @Captor ArgumentCaptor<AuditTrail.Entry> auditEntry;
  @Captor ArgumentCaptor<DomainEvent> event;
  @Captor ArgumentCaptor<WorkItem> itemCaptor;

  private CreateWorkItem service;

  @BeforeEach
  void setUp() {
    when(slaCalculator.getIfAvailable()).thenReturn(null);
    when(workflowEngine.getIfAvailable()).thenReturn(null);
    service = new CreateWorkItem(jdbc, store, audit, outbox, slaCalculator, workflowEngine, searchIndexer);
  }

  @Test
  void atomically_describes_audited_incident_and_outbox_event() {
    when(jdbc.queryForObject("SELECT nextval('work_item_number_seq')", Long.class)).thenReturn(1000L);

    var result = service.create(
        new CreateWorkItem.Command(Type.INCIDENT, "VPN unavailable", "Remote employees cannot connect", "Workplace"),
        "user-42"
    );

    assertThat(result.number()).isEqualTo("INC-001000");
    assertThat(result.state()).isEqualTo("NEW");
    assertThat(result.priority()).isEqualTo(Priority.MEDIUM.name());

    verify(store).insert(itemCaptor.capture());
    assertThat(itemCaptor.getValue().priority()).isEqualTo(Priority.MEDIUM);
    assertThat(itemCaptor.getValue().impact()).isEqualTo(Impact.MEDIUM);
    assertThat(itemCaptor.getValue().urgency()).isEqualTo(Urgency.MEDIUM);

    verify(audit).append(auditEntry.capture());
    verify(outbox).record(event.capture());
    verify(searchIndexer).index(itemCaptor.getValue());
    assertThat(auditEntry.getValue().actorId()).isEqualTo("user-42");
    assertThat(auditEntry.getValue().action()).isEqualTo("work-item.created");
    assertThat(event.getValue().type()).isEqualTo("incident.created");
    assertThat(event.getValue().correlationId()).isEqualTo(auditEntry.getValue().correlationId());
  }

  @Test
  void derives_critical_priority_from_high_impact_and_urgency() {
    when(jdbc.queryForObject("SELECT nextval('work_item_number_seq')", Long.class)).thenReturn(1001L);

    var result = service.create(
        new CreateWorkItem.Command(
            Type.INCIDENT,
            "Core switch down",
            "Site offline",
            "Network",
            Impact.HIGH,
            Urgency.HIGH,
            null,
            null
        ),
        "user-7"
    );

    assertThat(result.priority()).isEqualTo(Priority.CRITICAL.name());
    verify(store).insert(itemCaptor.capture());
    assertThat(itemCaptor.getValue().priority()).isEqualTo(Priority.CRITICAL);
  }

  @Test
  void starts_sla_when_calculator_is_available() {
    SlaDeadlineCalculator calculator = new SlaDeadlineCalculator();
    when(slaCalculator.getIfAvailable()).thenReturn(calculator);
    when(jdbc.queryForObject("SELECT nextval('work_item_number_seq')", Long.class)).thenReturn(1002L);

    service.create(
        new CreateWorkItem.Command(
            Type.SERVICE_REQUEST,
            "Access",
            "Need finance mart access",
            "Data",
            Impact.LOW,
            Urgency.LOW,
            null,
            null
        ),
        "user-1"
    );

    verify(store).startResponseSla(any(), any(Instant.class), any(Instant.class), any());
  }
}
