package ru.ultimavox.itsm.servicedesk.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;

class WorkItemStoreTest {
  @Test
  void insertScopesRecordToAuthenticatedOrganization() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    WorkItemStore store = new WorkItemStore(jdbc);
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    WorkItem item = new WorkItem(
        UUID.randomUUID(), "INC-1", WorkItem.Type.INCIDENT, "title", "description", "service",
        WorkItem.State.NEW, WorkItem.Priority.MEDIUM, WorkItem.Impact.MEDIUM,
        WorkItem.Urgency.MEDIUM, null, "requester", null, null, null, false,
        now, now, null, 0L);
    Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
        .claim("sub", "operator").claim("organization_id", "org-42").build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

    try {
      store.insert(item);
    } finally {
      SecurityContextHolder.clearContext();
    }

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).update(sql.capture(), args.capture());
    assertThat(sql.getValue()).contains("org_id");
    assertThat(args.getValue()[1]).isEqualTo("org-42");
  }

  @Test
  void staleVersionFailsInsteadOfOverwritingConcurrentChange() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    WorkItemStore store = new WorkItemStore(jdbc);
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    WorkItem item = new WorkItem(
        UUID.randomUUID(), "INC-1", WorkItem.Type.INCIDENT, "title", "description", "service",
        WorkItem.State.NEW, WorkItem.Priority.MEDIUM, WorkItem.Impact.MEDIUM,
        WorkItem.Urgency.MEDIUM, null, "requester", null, null, null, false,
        now, now, null, 7L);

    assertThatThrownBy(() -> store.update(item))
        .isInstanceOf(WorkItemConcurrencyException.class)
        .hasMessageContaining("version 7");

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).update(sql.capture(), any(Object[].class));
    org.assertj.core.api.Assertions.assertThat(sql.getValue())
        .contains("version = version + 1")
        .contains("WHERE id = ? AND org_id = ? AND version = ?");
  }

  @Test
  @SuppressWarnings("unchecked")
  void unassignedFilterUsesAssigneeIsNull() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    WorkItemStore store = new WorkItemStore(jdbc);
    when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any())).thenReturn(List.of());

    ru.ultimavox.itsm.platform.authorization.OrganizationContext.runAs("org-42", () -> {
      store.search(
          new WorkItemQuery.Filter(null, null, "agent-1", null, null, null, null, true, null, null, null, null),
          0,
          20
      );
      return null;
    });

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).query(sql.capture(), any(RowMapper.class), eq("org-42"), eq(20), eq(0));
    assertThat(sql.getValue()).contains("assignee_id IS NULL");
    assertThat(sql.getValue()).doesNotContain("assignee_id = ?");
  }

  @Test
  void countUnassignedUsesAssigneeIsNull() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    WorkItemStore store = new WorkItemStore(jdbc);
    when(jdbc.queryForObject(anyString(), eq(Long.class), any())).thenReturn(4L);

    long total = ru.ultimavox.itsm.platform.authorization.OrganizationContext.runAs("org-42",
        () -> store.countUnassigned(null));

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).queryForObject(sql.capture(), eq(Long.class), eq("org-42"));
    assertThat(sql.getValue()).contains("assignee_id IS NULL");
    assertThat(sql.getValue()).contains("state NOT IN ('CLOSED', 'CANCELLED')");
    assertThat(total).isEqualTo(4L);
  }

  @Test
  void countMineScopesToActorAndOpenStates() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    WorkItemStore store = new WorkItemStore(jdbc);
    when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any())).thenReturn(2L);

    long total = ru.ultimavox.itsm.platform.authorization.OrganizationContext.runAs("org-42",
        () -> store.countMine("agent-9", null));

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).queryForObject(sql.capture(), eq(Long.class), eq("org-42"), eq("agent-9"));
    assertThat(sql.getValue()).contains("assignee_id = ?");
    assertThat(sql.getValue()).contains("state NOT IN ('CLOSED', 'CANCELLED')");
    assertThat(total).isEqualTo(2L);
  }
}
