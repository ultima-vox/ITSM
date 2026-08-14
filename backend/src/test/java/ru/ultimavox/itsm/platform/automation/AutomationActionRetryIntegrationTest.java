package ru.ultimavox.itsm.platform.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.notification.NotificationRequest;
import ru.ultimavox.itsm.platform.notification.NotificationService;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.platform.search.SearchIndexService;

/**
 * End-to-end retry engine on a real database: a failing {@code notify} action is logged as FAILED,
 * a retry row is scheduled, and {@code retryDue} re-drives it — succeeding with exponential backoff
 * or quarantining a poisoned action after bounded attempts.
 */
@Testcontainers(disabledWithoutDocker = true)
class AutomationActionRetryIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  static JdbcTemplate jdbc;
  static AutomationRunner runner;
  static AutomationActionRetryService retry;
  static AutomationRuleAdminService admin;
  static AutomationActionLogQuery logQuery;
  static NotificationService notifications;
  static JdbcAutomationActionLogRepository actionLog;

  @BeforeAll
  static void setup() {
    var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(ds).load().migrate();
    jdbc = new JdbcTemplate(ds);
    var json = new ObjectMapper();

    notifications = mock(NotificationService.class);
    AllowlistedActionExecutor executor =
        new AllowlistedActionExecutor(notifications, mock(SearchIndexService.class), List.of());
    actionLog = new JdbcAutomationActionLogRepository(jdbc, json);
    JdbcAutomationRuleRepository ruleRepository = new JdbcAutomationRuleRepository(jdbc, json);
    admin = new AutomationRuleAdminService(
        ruleRepository, json, mock(AuditTrail.class), mock(IntegrationEventOutbox.class), executor);
    retry = new AutomationActionRetryService(jdbc, json, executor, actionLog, 2, Duration.ZERO, Duration.ofMinutes(10));
    runner = new AutomationRunner(ruleRepository, new ConditionEvaluator(), executor, actionLog, retry);
    logQuery = new AutomationActionLogQuery(jdbc, json);
  }

  @Test
  void retriesFailedActionUntilItSucceeds() {
    OrganizationContext.runAs("retry-ok-org", () -> {
      createNotifyRule("retry-ok-org");
      doThrow(new IllegalStateException("index down")).doNothing().when(notifications).send(any(NotificationRequest.class));

      DomainEvent event = event();
      assertThat(runner.handle(event)).isZero();

      assertThat(logQuery.list(null, null, 100, 0))
          .hasSize(1)
          .allMatch(e -> "FAILED".equals(e.status()) && e.attempts() == 1);
      assertThat(countRetries()).isEqualTo(1);

      assertThat(retry.retryDue(100)).isEqualTo(1);

      assertThat(logQuery.list(null, null, 100, 0))
          .hasSize(1)
          .allMatch(e -> "SUCCEEDED".equals(e.status()) && e.attempts() == 2
              && Boolean.TRUE.equals(e.details().get("retried")));
      assertThat(countRetries()).isZero();
      return null;
    });
  }

  @Test
  void quarantinesPoisonedActionAfterBoundedAttempts() {
    OrganizationContext.runAs("retry-poison-org", () -> {
      createNotifyRule("retry-poison-org");
      doThrow(new IllegalStateException("webhook unreachable")).when(notifications).send(any(NotificationRequest.class));

      DomainEvent event = event();
      assertThat(runner.handle(event)).isZero();
      assertThat(countRetries()).isEqualTo(1);

      assertThat(retry.retryDue(100)).isEqualTo(1);
      assertThat(countRetries()).isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT quarantined_at IS NOT NULL FROM automation_action_retry", Boolean.class))
          .isTrue();

      assertThat(retry.retryDue(100)).isZero();
      assertThat(logQuery.list(null, null, 100, 0))
          .hasSize(1)
          .allMatch(e -> "FAILED".equals(e.status()));
      return null;
    });
  }

  @Test
  void schedulesRetryIdempotentlyPerEvent() {
    OrganizationContext.runAs("retry-idem-org", () -> {
      createNotifyRule("retry-idem-org");
      doThrow(new IllegalStateException("down")).when(notifications).send(any(NotificationRequest.class));

      DomainEvent event = event();
      assertThat(runner.handle(event)).isZero();
      assertThat(runner.handle(event)).isZero();

      assertThat(logQuery.list(null, null, 100, 0)).hasSize(1);
      assertThat(countRetries()).isEqualTo(1);
      return null;
    });
  }

  private void createNotifyRule(String orgId) {
    admin.create("automation", new AutomationRuleAdminService.Command(
        "retry.notify." + orgId, "Notify on create", true,
        new AutomationRule.Trigger("work-item.created"),
        List.of(),
        List.of(new AutomationRule.Action("notify",
            Map.of("templateKey", "work-item.created", "recipientSubject", "oncall")))));
  }

  private static DomainEvent event() {
    return new DomainEvent(UUID.randomUUID(), "work-item.created", 1, Instant.now(), UUID.randomUUID(), null,
        "org", "actor-1", "work_item", UUID.randomUUID().toString(),
        Map.<String, Object>of("number", "INC-1"));
  }

  private int countRetries() {
    return jdbc.queryForObject("SELECT count(*) FROM automation_action_retry", Integer.class);
  }
}
