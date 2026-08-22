package ru.ultimavox.itsm.releasemanagement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
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
import ru.ultimavox.itsm.changemanagement.ChangeCatalogQuery;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.platform.workflow.WorkflowPolicyGateway;
import ru.ultimavox.itsm.releasemanagement.domain.Release;

@Testcontainers(disabledWithoutDocker = true)
class ReleaseLifecycleIntegrationTest {
  @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  static JdbcTemplate jdbc;
  static ReleaseQuery query;
  static ReleaseCommands commands;
  static ReleaseContentService content;

  @BeforeAll
  static void setup() {
    var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(ds).load().migrate();
    jdbc = new JdbcTemplate(ds);
    query = new ReleaseQuery(jdbc);
    content = new ReleaseContentService(jdbc, query, new TestChangeCatalog(jdbc), mock(AuditTrail.class));
    commands = new ReleaseCommands(jdbc, query, content, mock(AuditTrail.class),
        mock(IntegrationEventOutbox.class), mock(WorkflowPolicyGateway.class));
  }

  @Test
  void a_release_ships_only_after_its_gates_and_its_changes_are_ready() {
    OrganizationContext.runAs("release-" + UUID.randomUUID(), () -> {
      Instant start = Instant.now().plusSeconds(3600);
      Release created = commands.create(new ReleaseCommands.CreateCommand(
          "Payments 4.2", Release.Type.MINOR, "Quarterly release", null, null, "carol",
          start, start.plusSeconds(7200)), "carol");
      assertThat(created.number()).startsWith("REL-");
      assertThat(created.status()).isEqualTo(Release.Status.PLANNING);

      UUID changeId = insertChange("CHG-9001", "Upgrade the payment gateway", "DRAFT");
      List<ReleaseContentService.ContentEntry> linked =
          content.link(created.id(), List.of(changeId), "carol");
      assertThat(linked).singleElement().satisfies(entry -> {
        assertThat(entry.number()).isEqualTo("CHG-9001");
        assertThat(entry.deployable()).isFalse();
      });

      Release build = commands.transition(created.id(), Release.Status.BUILD, created.version(), "carol");
      assertThatThrownBy(() -> commands.transition(
          created.id(), Release.Status.TESTING, build.version(), "carol"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("rollback plan");

      Release planned = commands.update(created.id(), new ReleaseCommands.UpdateCommand(
          build.version(), null, null, null, "Blue-green switch", "Return traffic to blue",
          "Regression suite green", null, null, null), "carol");
      Release testing = commands.transition(created.id(), Release.Status.TESTING, planned.version(), "carol");
      Release review = commands.transition(created.id(), Release.Status.GO_NO_GO, testing.version(), "carol");

      assertThatThrownBy(() -> commands.transition(
          created.id(), Release.Status.DEPLOYING, review.version(), "carol"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("GO decision");

      Release decided = commands.recordGoDecision(
          created.id(), Release.GoDecision.GO, "CAB signed off", review.version(), "carol");
      assertThat(decided.goDecidedBy()).isEqualTo("carol");

      assertThatThrownBy(() -> commands.transition(
          created.id(), Release.Status.DEPLOYING, decided.version(), "carol"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("CHG-9001");

      jdbc.update("UPDATE change_request SET status = 'APPROVED' WHERE id = ?", changeId);
      assertThat(content.notReadyForDeployment(created.id())).isEmpty();

      Release deploying = commands.transition(
          created.id(), Release.Status.DEPLOYING, decided.version(), "carol");
      assertThat(deploying.actualStart()).isNotNull();

      assertThatThrownBy(() -> content.link(created.id(), List.of(changeId), "carol"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("cannot change its content");

      Release deployed = commands.transition(
          created.id(), Release.Status.DEPLOYED, deploying.version(), "carol");
      assertThat(deployed.actualEnd()).isNotNull();
      assertThat(deployed.version()).isEqualTo(deploying.version() + 1);
      return null;
    });
  }

  @Test
  void a_stale_write_is_rejected() {
    OrganizationContext.runAs("release-stale-" + UUID.randomUUID(), () -> {
      Release created = commands.create(new ReleaseCommands.CreateCommand(
          "Search 2.0", Release.Type.MAJOR, null, null, null, null, null, null), "dave");
      commands.transition(created.id(), Release.Status.BUILD, created.version(), "dave");
      assertThatThrownBy(() -> commands.transition(
          created.id(), Release.Status.TESTING, created.version(), "dave"))
          .isInstanceOf(OptimisticLockingFailureException.class);
      return null;
    });
  }

  @Test
  void releases_are_scoped_to_their_organization() {
    UUID id = OrganizationContext.runAs("release-org-a", () -> commands.create(
        new ReleaseCommands.CreateCommand("Org A release", Release.Type.PATCH, null, null, null,
            null, null, null), "dave").id());
    assertThat(OrganizationContext.runAs("release-org-b", () -> query.findById(id))).isEmpty();
    assertThat(OrganizationContext.runAs("release-org-a", () -> query.findById(id))).isPresent();
  }

  @Test
  void a_window_conflict_is_reported() {
    OrganizationContext.runAs("release-window-" + UUID.randomUUID(), () -> {
      Instant start = Instant.parse("2026-09-01T20:00:00Z");
      Release first = commands.create(new ReleaseCommands.CreateCommand(
          "Window A", Release.Type.MINOR, null, null, null, null, start, start.plusSeconds(7200)), "carol");
      List<Release> conflicts = query.findScheduleConflicts(
          start.plusSeconds(3600), start.plusSeconds(10800), null);
      assertThat(conflicts).extracting(Release::id).contains(first.id());
      assertThat(query.findScheduleConflicts(start.plusSeconds(3600), start.plusSeconds(10800), first.id()))
          .isEmpty();
      return null;
    });
  }

  private static UUID insertChange(String number, String title, String status) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
            INSERT INTO change_request (
              id, org_id, number, type, risk, status, title, implementation_plan, rollback_plan,
              requester_id, created_at, updated_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?, now(), now())
            """,
        id, OrganizationContext.current(), number + "-" + UUID.randomUUID(), "NORMAL", "MEDIUM",
        status, title, "Deploy", "Rollback", "carol");
    jdbc.update("UPDATE change_request SET number = ? WHERE id = ?", number, id);
    return id;
  }

  /** Stands in for the change module bean, reading the same public contract shape. */
  private record TestChangeCatalog(JdbcTemplate jdbc) implements ChangeCatalogQuery {
    @Override
    public List<ChangeSummary> summaries(Collection<UUID> ids) {
      if (ids == null || ids.isEmpty()) {
        return List.of();
      }
      String placeholders = String.join(", ", java.util.Collections.nCopies(ids.size(), "?"));
      Object[] args = new Object[ids.size() + 1];
      args[0] = OrganizationContext.current();
      int index = 1;
      for (UUID id : ids) {
        args[index++] = id;
      }
      return jdbc.query(
          ("SELECT id, number, title, type, status, planned_start, planned_end FROM change_request "
              + "WHERE org_id = ? AND id IN (%s) ORDER BY number").formatted(placeholders),
          (rs, row) -> new ChangeSummary(
              rs.getObject("id", UUID.class),
              rs.getString("number"),
              rs.getString("title"),
              rs.getString("type"),
              rs.getString("status"),
              rs.getTimestamp("planned_start") == null ? null : rs.getTimestamp("planned_start").toInstant(),
              rs.getTimestamp("planned_end") == null ? null : rs.getTimestamp("planned_end").toInstant()),
          args);
    }
  }
}
