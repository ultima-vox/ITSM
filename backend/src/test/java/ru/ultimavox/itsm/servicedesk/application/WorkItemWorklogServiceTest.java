package ru.ultimavox.itsm.servicedesk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;

@Testcontainers(disabledWithoutDocker = true)
class WorkItemWorklogServiceTest {
  @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  static JdbcTemplate jdbc;
  static WorkItemStore store;
  static WorkItemWorklogService service;

  @BeforeAll
  static void setup() {
    var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(ds).load().migrate();
    jdbc = new JdbcTemplate(ds);
    store = new WorkItemStore(jdbc);
    service = new WorkItemWorklogService(
        jdbc, store, mock(AuditTrail.class), mock(IntegrationEventOutbox.class));
  }

  @Test
  void logs_time_and_rolls_up_the_total_and_the_billable_share() {
    OrganizationContext.runAs("worklog-" + UUID.randomUUID(), () -> {
      UUID workItemId = insertWorkItem();
      Instant startedAt = Instant.now().minus(2, ChronoUnit.HOURS);

      service.log(workItemId, new WorkItemWorklogService.LogCommand(
          45, startedAt, "Reproduced the failure", true), "alice");
      service.log(workItemId, new WorkItemWorklogService.LogCommand(
          30, startedAt.plus(1, ChronoUnit.HOURS), "Wrote the fix", false), "alice");

      WorkItemWorklogService.Summary summary = service.list(workItemId);
      assertThat(summary.items()).hasSize(2);
      assertThat(summary.totalMinutes()).isEqualTo(75);
      assertThat(summary.billableMinutes()).isEqualTo(45);
      assertThat(summary.items().getFirst().startedAt())
          .isAfter(summary.items().getLast().startedAt());
      return null;
    });
  }

  @Test
  void rejects_impossible_entries() {
    OrganizationContext.runAs("worklog-invalid-" + UUID.randomUUID(), () -> {
      UUID workItemId = insertWorkItem();
      assertThatThrownBy(() -> service.log(workItemId, new WorkItemWorklogService.LogCommand(
          0, Instant.now(), null, false), "alice"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("minutes");
      assertThatThrownBy(() -> service.log(workItemId, new WorkItemWorklogService.LogCommand(
          2000, Instant.now(), null, false), "alice"))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> service.log(workItemId, new WorkItemWorklogService.LogCommand(
          30, Instant.now().plus(2, ChronoUnit.HOURS), null, false), "alice"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("future");
      return null;
    });
  }

  @Test
  void only_the_author_or_a_manager_may_change_an_entry() {
    OrganizationContext.runAs("worklog-owner-" + UUID.randomUUID(), () -> {
      UUID workItemId = insertWorkItem();
      WorkItemWorklogService.Entry entry = service.log(workItemId,
          new WorkItemWorklogService.LogCommand(20, Instant.now(), "Triage", false), "alice");

      assertThatThrownBy(() -> service.update(workItemId, entry.id(),
          new WorkItemWorklogService.UpdateCommand(60, null, null, null), "bob", false))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("author");
      assertThatThrownBy(() -> service.delete(workItemId, entry.id(), "bob", false))
          .isInstanceOf(IllegalStateException.class);

      WorkItemWorklogService.Entry corrected = service.update(workItemId, entry.id(),
          new WorkItemWorklogService.UpdateCommand(60, null, "Triage and handover", true),
          "alice", false);
      assertThat(corrected.minutes()).isEqualTo(60);
      assertThat(corrected.billable()).isTrue();
      assertThat(corrected.note()).isEqualTo("Triage and handover");

      service.delete(workItemId, entry.id(), "bob", true);
      assertThat(service.list(workItemId).items()).isEmpty();
      return null;
    });
  }

  @Test
  void entries_are_scoped_to_their_organization_and_their_work_item() {
    UUID scopedItem = OrganizationContext.runAs("worklog-org-a", () -> {
      UUID workItemId = insertWorkItem();
      service.log(workItemId, new WorkItemWorklogService.LogCommand(
          15, Instant.now(), null, false), "alice");
      return workItemId;
    });
    assertThat(OrganizationContext.runAs("worklog-org-a", () -> service.list(scopedItem).items()))
        .hasSize(1);
    assertThatThrownBy(() -> OrganizationContext.runAs("worklog-org-b", () -> service.list(scopedItem)))
        .isInstanceOf(WorkItemNotFoundException.class);

    OrganizationContext.runAs("worklog-org-a", () -> {
      UUID otherItem = insertWorkItem();
      UUID worklogId = service.list(scopedItem).items().getFirst().id();
      assertThatThrownBy(() -> service.delete(otherItem, worklogId, "alice", true))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Worklog not found");
      return null;
    });
  }

  private static UUID insertWorkItem() {
    Instant now = Instant.now();
    UUID id = UUID.randomUUID();
    store.insert(new WorkItem(
        id, "INC-" + Math.abs(id.hashCode()), WorkItem.Type.INCIDENT, "Payment failures",
        "Checkout returns 500", "payments", WorkItem.State.NEW, WorkItem.Priority.HIGH,
        WorkItem.Impact.HIGH, WorkItem.Urgency.HIGH, "alice", "requester", null, null, null,
        false, now, now, null, 0L));
    return id;
  }
}
