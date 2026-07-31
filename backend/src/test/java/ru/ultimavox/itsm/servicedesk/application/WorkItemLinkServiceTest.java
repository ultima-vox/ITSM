package ru.ultimavox.itsm.servicedesk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;
import ru.ultimavox.itsm.servicedesk.domain.WorkItemLink;

class WorkItemLinkServiceTest {

  private WorkItemStore store;
  private JdbcTemplate jdbc;
  private AuditTrail audit;
  private IntegrationEventOutbox outbox;
  private WorkItemLinkService service;

  private final UUID a = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private final UUID b = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

  @BeforeEach
  void setUp() {
    store = mock(WorkItemStore.class);
    jdbc = mock(JdbcTemplate.class);
    audit = mock(AuditTrail.class);
    outbox = mock(IntegrationEventOutbox.class);
    service = new WorkItemLinkService(store, jdbc, audit, outbox);
  }

  @Test
  void rejects_self_link() {
    assertThatThrownBy(() -> service.link(a, a, WorkItemLink.Type.RELATED, "agent-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("itself");
  }

  @Test
  void list_for_requires_existing_item() {
    when(store.requireById(a)).thenThrow(new WorkItemNotFoundException(a));
    assertThatThrownBy(() -> service.listFor(a))
        .isInstanceOf(WorkItemNotFoundException.class);
  }

  @Test
  @SuppressWarnings("unchecked")
  void link_inserts_and_audits() {
    when(store.requireById(any())).thenReturn(mock(WorkItem.class));
    when(jdbc.update(any(String.class), any(), any(), any(), any(), any(), any())).thenReturn(1);

    WorkItemLink link = service.link(a, b, WorkItemLink.Type.DUPLICATE_OF, "agent-1");

    assertThat(link.sourceId()).isEqualTo(a);
    assertThat(link.targetId()).isEqualTo(b);
    assertThat(link.linkType()).isEqualTo(WorkItemLink.Type.DUPLICATE_OF);
    verify(audit).append(any());
    verify(outbox).record(any());
  }

  @Test
  @SuppressWarnings("unchecked")
  void list_maps_rows() {
    when(store.requireById(a)).thenReturn(mock(WorkItem.class));
    when(jdbc.query(any(String.class), any(RowMapper.class), eq(a), eq(a)))
        .thenReturn(List.of(
            new WorkItemLink(
                UUID.randomUUID(), a, b, WorkItemLink.Type.RELATED, "agent-1",
                java.time.Instant.parse("2026-01-01T00:00:00Z")
            )
        ));

    List<WorkItemLink> links = service.listFor(a);
    assertThat(links).hasSize(1);
    assertThat(links.get(0).targetId()).isEqualTo(b);
  }
}
