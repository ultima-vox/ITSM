package ru.ultimavox.itsm.problemmanagement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

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
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.platform.workflow.WorkflowPolicyGateway;
import ru.ultimavox.itsm.problemmanagement.domain.Problem;
import ru.ultimavox.itsm.servicedesk.WorkItemReferenceQuery;

@Testcontainers(disabledWithoutDocker = true)
class ProblemOptimisticIntegrationTest {
  @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
  static ProblemCommands commands;

  @BeforeAll
  static void setup() {
    var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(ds).load().migrate();
    var jdbc = new JdbcTemplate(ds);
    commands = new ProblemCommands(jdbc, new ProblemQuery(jdbc), mock(AuditTrail.class),
        mock(IntegrationEventOutbox.class), mock(WorkflowPolicyGateway.class), mock(WorkItemReferenceQuery.class),
        mock(ProblemSearchIndexer.class));
  }

  @Test
  void rejectsStaleNotesAndTransitions() {
    OrganizationContext.runAs("problem-version-" + UUID.randomUUID(), () -> {
      Problem created = commands.create(new ProblemCommands.CreateCommand(
          "Intermittent VPN", null, null, null, null, null), "alice");
      assertThat(created.version()).isZero();
      Problem notes = commands.updateNotes(created.id(), "Gateway race", "Restart tunnel", null,
          null, null, null, 0, "alice");
      assertThat(notes.version()).isEqualTo(1);
      assertThatThrownBy(() -> commands.updateNotes(created.id(), "stale", null, null,
          null, null, null, 0, "alice"))
          .isInstanceOf(OptimisticLockingFailureException.class);
      Problem investigating = commands.transition(created.id(), Problem.Status.UNDER_INVESTIGATION,
          null, null, null, 1L, "alice");
      assertThat(investigating.version()).isEqualTo(2);
      return null;
    });
  }
}
