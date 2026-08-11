package ru.ultimavox.itsm.changemanagement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.ultimavox.itsm.changemanagement.domain.Change;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.platform.workflow.WorkflowPolicyGateway;

@Testcontainers
class ChangeOptimisticIntegrationTest {
  @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
  static ChangeCommands commands;

  @BeforeAll
  static void setup() {
    var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(ds).load().migrate();
    var jdbc = new JdbcTemplate(ds);
    commands = new ChangeCommands(jdbc, new ChangeQuery(jdbc), mock(AuditTrail.class),
        mock(IntegrationEventOutbox.class), mock(WorkflowPolicyGateway.class));
  }

  @Test
  void rejectsStaleLifecycleWrite() {
    OrganizationContext.runAs("change-version-" + UUID.randomUUID(), () -> {
      Instant start = Instant.now().plusSeconds(3600);
      Change created = commands.create(new ChangeCommands.CreateCommand(
          Change.Type.NORMAL, Change.Risk.MEDIUM, "Deploy gateway", start, start.plusSeconds(3600),
          "Deploy", "Rollback", "Reliability", null, null), "alice");
      assertThat(created.version()).isZero();
      Change submitted = commands.transition(created.id(), Change.Status.SUBMITTED, null, null, 0L, "alice");
      assertThat(submitted.version()).isEqualTo(1);
      assertThatThrownBy(() -> commands.transition(
          created.id(), Change.Status.CAB_REVIEW, null, null, 0L, "alice"))
          .isInstanceOf(OptimisticLockingFailureException.class);
      return null;
    });
  }
}
