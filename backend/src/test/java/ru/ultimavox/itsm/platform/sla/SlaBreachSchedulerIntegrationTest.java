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

/**
 * End-to-end SLA breach/warning sweep on a real database: RUNNING clocks past their deadlines are
 * marked BREACHED, clocks inside the warning window are warned exactly once, events are recorded
 * per organization, and a tenant's automation rule fires on the {@code sla.breached} event.
 */
@Testcontainers(disabledWithoutDocker = true)
class SlaBreachSchedulerIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  static JdbcTemplate jdbc;
  static JdbcIntegrationEventOutbox outbox;
  static SlaService sla;
  static AutomationRunner runner;
  static AutomationRuleAdminService ruleAdmin;

  @BeforeAll
  static void setup() {
    var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(ds).load().migrate();
    jdbc = new JdbcTemplate(ds);
    var json = new ObjectMapper();

    outbox = new JdbcIntegrationEventOutbox(jdbc, json, event -> {});
    WorkingCalendarRegistry calendars = new WorkingCalendarRegistry(jdbc);
    sla = new SlaService(
        new JdbcSlaPolicyRepository(jdbc, json),
        new JdbcSlaClockRepository(jdbc),
        new SlaDeadlineCalculator(),
        calendars,
        outbox,
        json);

    AllowlistedActionExecutor executor = new AllowlistedActionExecutor(
        mock(NotificationService.class), mock(SearchIndexService.class), List.of());
    JdbcAutomationActionLogRepository actionLog = new JdbcAutomationActionLogRepository(jdbc, json);
    ruleAdmin = new AutomationRuleAdminService(
        new JdbcAutomationRuleRepository(jdbc, json), json, mock(AuditTrail.class), outbox, executor);
    runner = new AutomationRunner(
        new JdbcAutomationRuleRepository(jdbc, json), new ConditionEvaluator(), executor, actionLog,
        new AutomationActionRetryService(jdbc, json, executor, actionLog, 5, Duration.ZERO, Duration.ofMinutes(10)));
  }

  @Test
  void sweepBreachesPastDueClocksAndEmitsPerOrgEvents() {
    OrganizationContext.runAs("sla-org-a", () -> {
      UUID breachId = UUID.randomUUID();
      UUID warnId = UUID.randomUUID();
      Instant now = Instant.now();
      insertClock(breachId, "sla-org-a", now.minusSeconds(300), now.minusSeconds(60), null, "RUNNING");
      insertClock(warnId, "sla-org-a", now.minusSeconds(600), now.plusSeconds(300), now.minusSeconds(60), "RUNNING");

      int breached = sla.detectBreaches(200);
      int warned = sla.detectWarnings(200);

      assertThat(breached).isEqualTo(1);
      assertThat(warned).isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT state FROM sla_clock WHERE id = ?", String.class, breachId))
          .isEqualTo("BREACHED");
      assertThat(jdbc.queryForObject("SELECT warned_at FROM sla_clock WHERE id = ?", Timestamp.class, warnId))
          .isNotNull();
      assertThat(outboxAggregateIds("sla.breached", "sla-org-a")).contains(breachId.toString());
      assertThat(outboxAggregateIds("sla.warning", "sla-org-a")).contains(warnId.toString());

      // A second sweep warns nothing: warned_at deduplicates.
      assertThat(sla.detectWarnings(200)).isZero();
      return null;
    });
  }

  @Test
  void pastDueClockIsNotWarned() {
    OrganizationContext.runAs("sla-org-b", () -> {
      UUID id = UUID.randomUUID();
      Instant now = Instant.now();
      insertClock(id, "sla-org-b", now.minusSeconds(600), now.minusSeconds(30), now.minusSeconds(120), "RUNNING");

      assertThat(sla.detectWarnings(200)).isZero(); // past due_at: warning window already passed
      assertThat(sla.detectBreaches(200)).isEqualTo(1);
      assertThat(outboxAggregateIds("sla.breached", "sla-org-b")).contains(id.toString());
      return null;
    });
  }

  @Test
  void automationRuleFiresOnSlaBreachEvent() {
    OrganizationContext.runAs("sla-org-c", () -> {
      ruleAdmin.create("automation", new AutomationRuleAdminService.Command(
          "auto.sla.watch", "Log SLA breaches", true,
          new AutomationRule.Trigger("sla.breached"),
          List.of(),
          List.of(new AutomationRule.Action("log", Map.of("message", "breach detected")))));

      UUID clockId = UUID.randomUUID();
      insertClock(clockId, "sla-org-c", Instant.now().minusSeconds(300), Instant.now().minusSeconds(60), null, "RUNNING");
      sla.detectBreaches(200);

      DomainEvent breachEvent = outboxEvents("sla.breached", "sla-org-c").get(0);
      assertThat(runner.handle(breachEvent)).isEqualTo(1);
      assertThat(jdbc.queryForObject(
          "SELECT status FROM automation_action_log WHERE rule_key = ? AND event_id = ?",
          String.class, "auto.sla.watch", breachEvent.id())).isEqualTo("SUCCEEDED");
      return null;
    });
  }

  @Test
  void distinctOrgsAreReportedAndOnlyTheirOwnClocksSweep() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    insertClock(a, "sla-org-x", Instant.now().minusSeconds(300), Instant.now().minusSeconds(60), null, "RUNNING");
    insertClock(b, "sla-org-y", Instant.now().minusSeconds(300), Instant.now().plusSeconds(60), null, "RUNNING");

    assertThat(new JdbcSlaClockRepository(jdbc).distinctOrgIdsWithDueOrWarnClocks())
        .containsExactlyInAnyOrder("sla-org-x", "sla-org-y");

    OrganizationContext.runAs("sla-org-x", () -> {
      assertThat(sla.detectBreaches(200)).isEqualTo(1);
      assertThat(sla.detectWarnings(200)).isZero();
      return null;
    });
    // The other organization's clock is untouched.
    assertThat(jdbc.queryForObject("SELECT state FROM sla_clock WHERE id = ?", String.class, b))
        .isEqualTo("RUNNING");
  }

  private void insertClock(UUID id, String orgId, Instant startedAt, Instant dueAt, Instant warningAt, String state) {
    jdbc.update("""
            INSERT INTO sla_clock (id, org_id, policy_key, aggregate_id, metric, started_at, due_at, warning_at, state)
            VALUES (?,?,?,?,?,?,?,?,?)
            """,
        id, orgId, "p.breach", UUID.randomUUID(), "resolution-time",
        Timestamp.from(startedAt), Timestamp.from(dueAt),
        warningAt == null ? null : Timestamp.from(warningAt), state);
  }

  private List<String> outboxAggregateIds(String type, String orgId) {
    return jdbc.queryForList(
        "SELECT aggregate_id FROM outbox_event WHERE event_type = ? AND organization_id = ? ORDER BY occurred_at",
        String.class, type, orgId);
  }

  private List<DomainEvent> outboxEvents(String type, String orgId) {
    return jdbc.query(
        "SELECT id, event_type, schema_version, occurred_at, correlation_id, causation_id, organization_id, "
            + "actor_id, aggregate_type, aggregate_id FROM outbox_event "
            + "WHERE event_type = ? AND organization_id = ? ORDER BY occurred_at",
        (rs, i) -> new DomainEvent(
            UUID.fromString(rs.getString("id")), rs.getString("event_type"), rs.getInt("schema_version"),
            rs.getTimestamp("occurred_at").toInstant(), UUID.fromString(rs.getString("correlation_id")),
            rs.getString("causation_id") == null ? null : UUID.fromString(rs.getString("causation_id")),
            rs.getString("organization_id"), rs.getString("actor_id"), rs.getString("aggregate_type"),
            rs.getString("aggregate_id"), Map.of()),
        type, orgId);
  }
}
