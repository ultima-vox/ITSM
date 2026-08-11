package ru.ultimavox.itsm.platform.sla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
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

@Testcontainers
class WorkingCalendarAdminIntegrationTest {
  @Container static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine");
  static WorkingCalendarRegistry registry;
  static WorkingCalendarAdminService admin;

  @BeforeAll
  static void setup() {
    var ds = new DriverManagerDataSource(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(ds).load().migrate();
    var jdbc = new JdbcTemplate(ds);
    registry = new WorkingCalendarRegistry(jdbc);
    admin = new WorkingCalendarAdminService(jdbc, registry,
        mock(AuditTrail.class), mock(IntegrationEventOutbox.class));
  }

  @Test
  void persistsTenantCalendarAndRejectsStaleWrite() {
    String org = "calendar-" + UUID.randomUUID();
    OrganizationContext.runAs(org, () -> {
      assertThat(admin.list()).extracting(WorkingCalendarRegistry.WorkingCalendarView::key)
          .contains("default-business");
      var created = admin.create("admin", new WorkingCalendarAdminService.Command(
          "support-24x5", "UTC", Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
          LocalTime.of(8, 0), LocalTime.of(20, 0), Set.of(LocalDate.of(2027, 1, 1))));
      assertThat(created.version()).isZero();
      assertThat(registry.require("support-24x5").zone().getId()).isEqualTo("UTC");

      var updated = admin.update("admin", created.id(), 0,
          new WorkingCalendarAdminService.Command("support-24x5", "Europe/Moscow",
              Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), LocalTime.of(9, 0),
              LocalTime.of(18, 0), Set.of()));
      assertThat(updated.version()).isOne();
      assertThat(updated.calendar().zone().getId()).isEqualTo("Europe/Moscow");
      assertThatThrownBy(() -> admin.update("admin", created.id(), 0,
          new WorkingCalendarAdminService.Command("support-24x5", "UTC",
              Set.of(DayOfWeek.MONDAY), LocalTime.of(9, 0), LocalTime.of(18, 0), Set.of())))
          .isInstanceOf(OptimisticLockingFailureException.class);
      return null;
    });
  }
}
