package ru.ultimavox.itsm.platform.oncall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

@Testcontainers(disabledWithoutDocker = true)
class OnCallDirectoryIntegrationTest {
  @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  private static final Instant ROTATION_START = Instant.parse("2026-08-03T09:00:00Z");

  static OnCallAdminService admin;
  static OnCallDirectory directory;

  @BeforeAll
  static void setup() {
    var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(ds).load().migrate();
    JdbcTemplate jdbc = new JdbcTemplate(ds);
    admin = new OnCallAdminService(jdbc, mock(AuditTrail.class));
    directory = new JdbcOnCallDirectory(jdbc);
  }

  @Test
  void the_rotation_decides_who_answers_and_an_override_wins() {
    OrganizationContext.runAs("oncall-" + UUID.randomUUID(), () -> {
      admin.createSchedule(new OnCallAdminService.ScheduleCommand(
          "platform", "Platform rota", "Europe/Berlin", 168, ROTATION_START, true,
          List.of("alice", "bob", "carol")), "admin");

      assertThat(directory.onCall("platform", ROTATION_START.plus(1, ChronoUnit.DAYS)))
          .contains("alice");
      assertThat(directory.onCall("platform", ROTATION_START.plus(8, ChronoUnit.DAYS)))
          .contains("bob");
      assertThat(directory.onCall("platform", ROTATION_START.plus(15, ChronoUnit.DAYS)))
          .contains("carol");

      Instant coverStart = ROTATION_START.plus(1, ChronoUnit.DAYS);
      admin.addOverride("platform", new OnCallAdminService.OverrideCommand(
          "dave", coverStart, coverStart.plus(12, ChronoUnit.HOURS), "Alice at the dentist"), "admin");

      assertThat(directory.onCall("platform", coverStart.plus(1, ChronoUnit.HOURS))).contains("dave");
      assertThat(directory.onCall("platform", coverStart.plus(13, ChronoUnit.HOURS))).contains("alice");
      return null;
    });
  }

  @Test
  void an_unknown_or_inactive_schedule_answers_with_nobody() {
    OrganizationContext.runAs("oncall-inactive-" + UUID.randomUUID(), () -> {
      assertThat(directory.onCall("missing", Instant.now())).isEmpty();
      admin.createSchedule(new OnCallAdminService.ScheduleCommand(
          "night", "Night rota", "UTC", 24, ROTATION_START, false, List.of("alice")), "admin");
      assertThat(directory.onCall("night", ROTATION_START.plus(1, ChronoUnit.DAYS))).isEmpty();
      return null;
    });
  }

  @Test
  void an_escalation_chain_resolves_schedules_to_the_subject_on_call() {
    OrganizationContext.runAs("oncall-chain-" + UUID.randomUUID(), () -> {
      admin.createSchedule(new OnCallAdminService.ScheduleCommand(
          "platform", "Platform rota", "UTC", 168, ROTATION_START, true,
          List.of("alice", "bob")), "admin");
      admin.createPolicy(new OnCallAdminService.PolicyCommand(
          "work-item.escalation", "Major incident escalation", true,
          List.of(
              new OnCallAdminService.StepCommand(0, "SCHEDULE", "platform"),
              new OnCallAdminService.StepCommand(15, "SUBJECT", "duty-manager"),
              new OnCallAdminService.StepCommand(30, "SCHEDULE", "missing"))), "admin");

      List<OnCallDirectory.Responder> chain = directory.escalationChain(
          "work-item.escalation", ROTATION_START.plus(1, ChronoUnit.DAYS));

      assertThat(chain).hasSize(2);
      assertThat(chain.getFirst().subject()).isEqualTo("alice");
      assertThat(chain.getFirst().delayMinutes()).isZero();
      assertThat(chain.get(1).subject()).isEqualTo("duty-manager");
      assertThat(chain.get(1).delayMinutes()).isEqualTo(15);
      assertThat(directory.firstResponder("work-item.escalation", ROTATION_START.plus(8, ChronoUnit.DAYS)))
          .map(OnCallDirectory.Responder::subject)
          .contains("bob");
      return null;
    });
  }

  @Test
  void schedules_and_policies_are_scoped_to_their_organization() {
    OrganizationContext.runAs("oncall-org-a", () -> admin.createSchedule(
        new OnCallAdminService.ScheduleCommand(
            "shared-key", "Org A rota", "UTC", 24, ROTATION_START, true, List.of("alice")),
        "admin"));

    assertThat(OrganizationContext.runAs("oncall-org-b",
        () -> directory.onCall("shared-key", ROTATION_START.plus(1, ChronoUnit.HOURS)))).isEmpty();
    assertThat(OrganizationContext.runAs("oncall-org-a",
        () -> directory.onCall("shared-key", ROTATION_START.plus(1, ChronoUnit.HOURS)))).contains("alice");
  }

  @Test
  void invalid_definitions_are_refused() {
    OrganizationContext.runAs("oncall-invalid-" + UUID.randomUUID(), () -> {
      assertThatThrownBy(() -> admin.createSchedule(new OnCallAdminService.ScheduleCommand(
          "empty", "No participants", "UTC", 24, ROTATION_START, true, List.of()), "admin"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("participant");
      assertThatThrownBy(() -> admin.createSchedule(new OnCallAdminService.ScheduleCommand(
          "bad-zone", "Bad zone", "Mars/Olympus", 24, ROTATION_START, true, List.of("alice")), "admin"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("time zone");
      assertThatThrownBy(() -> admin.createPolicy(new OnCallAdminService.PolicyCommand(
          "backwards", "Backwards delays", true,
          List.of(
              new OnCallAdminService.StepCommand(30, "SUBJECT", "alice"),
              new OnCallAdminService.StepCommand(5, "SUBJECT", "bob"))), "admin"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("non-decreasing");

      admin.createSchedule(new OnCallAdminService.ScheduleCommand(
          "dup", "Duplicate", "UTC", 24, ROTATION_START, true, List.of("alice")), "admin");
      assertThatThrownBy(() -> admin.createSchedule(new OnCallAdminService.ScheduleCommand(
          "dup", "Duplicate", "UTC", 24, ROTATION_START, true, List.of("bob")), "admin"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("already exists");
      return null;
    });
  }
}
