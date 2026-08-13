package ru.ultimavox.itsm.platform.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.notification.NotificationService;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.platform.search.SearchIndexService;
import ru.ultimavox.itsm.servicedesk.application.AssignWorkItem;
import ru.ultimavox.itsm.servicedesk.application.WorkItemAssignAutomationAction;
import ru.ultimavox.itsm.servicedesk.application.WorkItemStore;

/**
 * End-to-end automation chain on a real database: an admin rule with an {@code assign} action
 * fires on {@code incident.created} and the work item's assignee is updated transactionally,
 * once per event.
 */
@Testcontainers(disabledWithoutDocker = true)
class AutomationAssignWorkItemIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  static JdbcTemplate jdbc;
  static AutomationRunner runner;
  static AutomationRuleAdminService admin;

  @BeforeAll
  static void setup() {
    var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(ds).load().migrate();
    jdbc = new JdbcTemplate(ds);
    var json = new ObjectMapper();

    WorkItemStore store = new WorkItemStore(jdbc);
    AssignWorkItem assignWorkItem = new AssignWorkItem(
        store, mock(AuditTrail.class), mock(IntegrationEventOutbox.class), mock(NotificationService.class));
    WorkItemAssignAutomationAction assignHandler = new WorkItemAssignAutomationAction(assignWorkItem);
    AllowlistedActionExecutor executor = new AllowlistedActionExecutor(
        mock(NotificationService.class), mock(SearchIndexService.class), List.of(assignHandler));

    JdbcAutomationRuleRepository ruleRepository = new JdbcAutomationRuleRepository(jdbc, json);
    admin = new AutomationRuleAdminService(
        ruleRepository, json, mock(AuditTrail.class), mock(IntegrationEventOutbox.class), executor);
    runner = new AutomationRunner(
        ruleRepository, new ConditionEvaluator(), executor,
        new JdbcAutomationActionLogRepository(jdbc, json));
  }

  @Test
  void assignsFreshIncidentToTheRequesterViaRule() {
    OrganizationContext.runAs("auto-org", () -> {
      UUID itemId = UUID.randomUUID();
      insertWorkItem(itemId, "INC-000777", "auto-org");
      createAssignRule("auto-org");

      DomainEvent created = new DomainEvent(
          UUID.randomUUID(), "incident.created", 1, Instant.now(), UUID.randomUUID(), null,
          "auto-org", "requester-1", "work-item", itemId.toString(),
          Map.<String, Object>of("requesterId", "requester-1", "service", "Print"));

      int executed = runner.handle(created);

      assertThat(executed).isEqualTo(1);
      assertThat(jdbc.queryForObject(
          "SELECT assignee_id FROM work_item WHERE id = ? AND org_id = ?",
          String.class, itemId, "auto-org")).isEqualTo("requester-1");
      return null;
    });
  }

  @Test
  void assignActionIsIdempotentPerEvent() {
    OrganizationContext.runAs("auto-org-2", () -> {
      UUID itemId = UUID.randomUUID();
      insertWorkItem(itemId, "INC-000778", "auto-org-2");
      createAssignRule("auto-org-2");

      DomainEvent created = new DomainEvent(
          UUID.randomUUID(), "incident.created", 1, Instant.now(), UUID.randomUUID(), null,
          "auto-org-2", "requester-2", "work-item", itemId.toString(),
          Map.<String, Object>of("requesterId", "requester-2"));

      assertThat(runner.handle(created)).isEqualTo(1);
      assertThat(runner.handle(created)).isZero();
      return null;
    });
  }

  private void createAssignRule(String orgId) {
    admin.create("automation", new AutomationRuleAdminService.Command(
        "auto.assign.requester", "Assign to requester", true,
        new AutomationRule.Trigger("incident.created"),
        List.of(),
        List.of(new AutomationRule.Action("assign",
            Map.of("assigneeId", "{{data.requesterId}}")))));
  }

  private void insertWorkItem(UUID id, String number, String orgId) {
    Instant now = Instant.now();
    jdbc.update("""
            INSERT INTO work_item (
              id, org_id, number, type, title, description, service, state, priority,
              impact, urgency, assignee_id, requester_id, team_id,
              resolution_code, resolution_notes, escalated, created_at, updated_at, version
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,
        id, orgId, number, "INCIDENT", "Printer down", "Smoke on the paper tray", "Print",
        "NEW", "HIGH", "HIGH", "MEDIUM", null, "requester-1", "helpdesk",
        null, null, false, Timestamp.from(now), Timestamp.from(now), 0L);
  }
}
