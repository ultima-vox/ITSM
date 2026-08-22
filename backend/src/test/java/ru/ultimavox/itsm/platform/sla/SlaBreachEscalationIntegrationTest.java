package ru.ultimavox.itsm.platform.sla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
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
import ru.ultimavox.itsm.platform.automation.AllowlistedActionExecutor;
import ru.ultimavox.itsm.platform.automation.AutomationActionRetryService;
import ru.ultimavox.itsm.platform.automation.AutomationRule;
import ru.ultimavox.itsm.platform.automation.AutomationRuleAdminService;
import ru.ultimavox.itsm.platform.automation.AutomationRunner;
import ru.ultimavox.itsm.platform.automation.ConditionEvaluator;
import ru.ultimavox.itsm.platform.automation.JdbcAutomationActionLogRepository;
import ru.ultimavox.itsm.platform.automation.JdbcAutomationRuleRepository;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.notification.NotificationService;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.platform.outbox.JdbcIntegrationEventOutbox;
import ru.ultimavox.itsm.platform.search.SearchIndexService;
import ru.ultimavox.itsm.servicedesk.application.EscalateWorkItem;
import ru.ultimavox.itsm.servicedesk.application.WorkItemEscalateAutomationAction;
import ru.ultimavox.itsm.servicedesk.application.WorkItemStore;
import ru.ultimavox.itsm.servicedesk.application.WorkItemSearchIndexer;

/**
 * End-to-end SLA breach escalation on a real database: a breached work-item response clock emits
 * {@code sla.breached}, a tenant automation rule fires the allowlisted {@code escalate} action,
 * and the work item is raised to CRITICAL, flagged escalated and moved to IN_PROGRESS.
 */
@Testcontainers(disabledWithoutDocker = true)
class SlaBreachEscalationIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  static JdbcTemplate jdbc;
  static SlaService sla;
  static AutomationRunner runner;
  static AutomationRuleAdminService ruleAdmin;

  @BeforeAll
  static void setup() {
    var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(ds).load().migrate();
    jdbc = new JdbcTemplate(ds);
    var json = new ObjectMapper().findAndRegisterModules();

    IntegrationEventOutbox realOutbox = new JdbcIntegrationEventOutbox(jdbc, json, event -> {});
    sla = new SlaService(
        new JdbcSlaPolicyRepository(jdbc, json),
        new JdbcSlaClockRepository(jdbc),
        new SlaDeadlineCalculator(),
        new WorkingCalendarRegistry(jdbc),
        realOutbox,
        json);

    WorkItemStore store = new WorkItemStore(jdbc);
    EscalateWorkItem escalateWorkItem = new EscalateWorkItem(
        store, mock(AuditTrail.class), mock(IntegrationEventOutbox.class), mock(NotificationService.class),
        new WorkItemSearchIndexer(mock(SearchIndexService.class)),
        mock(ru.ultimavox.itsm.platform.oncall.OnCallDirectory.class));
    AllowlistedActionExecutor executor = new AllowlistedActionExecutor(
        mock(NotificationService.class), mock(SearchIndexService.class),
        List.of(new WorkItemEscalateAutomationAction(escalateWorkItem)));
    JdbcAutomationActionLogRepository actionLog = new JdbcAutomationActionLogRepository(jdbc, json);
    ruleAdmin = new AutomationRuleAdminService(
        new JdbcAutomationRuleRepository(jdbc, json), json, mock(AuditTrail.class), realOutbox, executor);
    runner = new AutomationRunner(
        new JdbcAutomationRuleRepository(jdbc, json), new ConditionEvaluator(), executor, actionLog,
        new AutomationActionRetryService(jdbc, json, executor, actionLog, 5, Duration.ZERO, Duration.ofMinutes(10)));
  }

  @Test
  void breachedSlaClockEscalatesItsWorkItemViaAutomation() {
    OrganizationContext.runAs("sla-escalate-org", () -> {
      UUID itemId = UUID.randomUUID();
      insertWorkItem(itemId, "INC-001001", "sla-escalate-org");
      insertClock(itemId, "sla-escalate-org", Instant.now().minusSeconds(300), Instant.now().minusSeconds(60));
      ruleAdmin.create("automation", new AutomationRuleAdminService.Command(
          "sla.escalate.breach", "Escalate breached SLA", true,
          new AutomationRule.Trigger("sla.breached"),
          List.of(),
          List.of(new AutomationRule.Action("escalate",
              Map.of("workItemId", "{{data.aggregateId}}")))));

      sla.detectBreaches(200);
      DomainEvent breachEvent = breachEventFor("sla-escalate-org", itemId);

      assertThat(runner.handle(breachEvent)).isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT escalated FROM work_item WHERE id = ?", Boolean.class, itemId))
          .isTrue();
      assertThat(jdbc.queryForObject("SELECT state FROM work_item WHERE id = ?", String.class, itemId))
          .isEqualTo("IN_PROGRESS");
      assertThat(jdbc.queryForObject("SELECT priority FROM work_item WHERE id = ?", String.class, itemId))
          .isEqualTo("CRITICAL");
      return null;
    });
  }

  @Test
  void escalationIsIdempotentPerBreachEvent() {
    OrganizationContext.runAs("sla-escalate-org-2", () -> {
      UUID itemId = UUID.randomUUID();
      insertWorkItem(itemId, "INC-001002", "sla-escalate-org-2");
      insertClock(itemId, "sla-escalate-org-2", Instant.now().minusSeconds(300), Instant.now().minusSeconds(60));
      ruleAdmin.create("automation", new AutomationRuleAdminService.Command(
          "sla.escalate.breach", "Escalate breached SLA", true,
          new AutomationRule.Trigger("sla.breached"),
          List.of(),
          List.of(new AutomationRule.Action("escalate", Map.of()))));

      sla.detectBreaches(200);
      DomainEvent breachEvent = breachEventFor("sla-escalate-org-2", itemId);

      assertThat(runner.handle(breachEvent)).isEqualTo(1);
      assertThat(runner.handle(breachEvent)).isZero(); // action log dedupes per event
      return null;
    });
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

  private void insertClock(UUID workItemId, String orgId, Instant startedAt, Instant dueAt) {
    jdbc.update("""
            INSERT INTO sla_clock (id, org_id, policy_key, aggregate_id, metric, started_at, due_at, warning_at, state)
            VALUES (?,?,?,?,?,?,?,?,?)
            """,
        UUID.randomUUID(), orgId, "work-item.response.default", workItemId, "response",
        Timestamp.from(startedAt), Timestamp.from(dueAt), null, "RUNNING");
  }

  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

  private DomainEvent breachEventFor(String orgId, UUID workItemId) {
    List<DomainEvent> events = jdbc.query(
        "SELECT id, event_type, schema_version, occurred_at, correlation_id, causation_id, organization_id, "
            + "actor_id, aggregate_type, aggregate_id, payload FROM outbox_event "
            + "WHERE event_type = 'sla.breached' AND organization_id = ? "
            + "AND payload -> 'data' ->> 'aggregateId' = ?",
        (rs, i) -> {
          String workItem;
          try {
            workItem = json.readTree(rs.getString("payload")).path("data").path("aggregateId").asText();
          } catch (Exception ex) {
            throw new IllegalStateException("Cannot parse outbox payload", ex);
          }
          return new DomainEvent(
              UUID.fromString(rs.getString("id")), rs.getString("event_type"), rs.getInt("schema_version"),
              rs.getTimestamp("occurred_at").toInstant(), UUID.fromString(rs.getString("correlation_id")),
              rs.getString("causation_id") == null ? null : UUID.fromString(rs.getString("causation_id")),
              rs.getString("organization_id"), rs.getString("actor_id"), rs.getString("aggregate_type"),
              rs.getString("aggregate_id"), Map.of("aggregateId", workItem));
        },
        orgId, workItemId.toString());
    assertThat(events).isNotEmpty();
    return events.get(0);
  }
}
