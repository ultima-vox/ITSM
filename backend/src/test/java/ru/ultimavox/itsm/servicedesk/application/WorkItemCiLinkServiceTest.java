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

class WorkItemCiLinkServiceTest {

  private WorkItemStore store;
  private JdbcTemplate jdbc;
  private AuditTrail audit;
  private IntegrationEventOutbox outbox;
  private WorkItemCiLinkService service;

  private final UUID wi = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private final UUID ci = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

  @BeforeEach
  void setUp() {
    store = mock(WorkItemStore.class);
    jdbc = mock(JdbcTemplate.class);
    audit = mock(AuditTrail.class);
    outbox = mock(IntegrationEventOutbox.class);
    service = new WorkItemCiLinkService(store, jdbc, audit, outbox);
  }

  @Test
  void link_rejects_missing_ci() {
    when(store.requireById(wi)).thenReturn(mock(WorkItem.class));
    when(jdbc.queryForObject(any(String.class), eq(Integer.class), eq(ci))).thenReturn(0);
    assertThatThrownBy(() -> service.link(wi, ci, "agent-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Configuration item");
  }

  @Test
  @SuppressWarnings("unchecked")
  void link_inserts_and_returns_list() {
    when(store.requireById(wi)).thenReturn(mock(WorkItem.class));
    when(jdbc.queryForObject(any(String.class), eq(Integer.class), eq(ci))).thenReturn(1);
    when(jdbc.update(any(String.class), any(), any(), any(), any())).thenReturn(1);
    when(jdbc.query(any(String.class), any(RowMapper.class), eq(wi))).thenReturn(List.of(ci));

    List<UUID> linked = service.link(wi, ci, "agent-1");
    assertThat(linked).containsExactly(ci);
    verify(audit).append(any());
    verify(outbox).record(any());
  }
}
