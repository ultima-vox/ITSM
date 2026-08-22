package ru.ultimavox.itsm.platform.announcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

@Testcontainers(disabledWithoutDocker = true)
class AnnouncementServiceTest {
  @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  static AnnouncementService service;

  @BeforeAll
  static void setup() {
    var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(ds).load().migrate();
    service = new AnnouncementService(new JdbcTemplate(ds), mock(AuditTrail.class));
  }

  private static AnnouncementService.Command command(
      String title, AnnouncementService.Severity severity, AnnouncementService.Audience audience,
      Instant startsAt, Instant endsAt, boolean published) {
    return new AnnouncementService.Command(
        title, "Body of " + title, severity, audience, startsAt, endsAt, published, true, null);
  }

  @Test
  void only_published_announcements_inside_their_window_are_active() {
    OrganizationContext.runAs("announce-" + UUID.randomUUID(), () -> {
      Instant now = Instant.now();
      service.create(command("Live", AnnouncementService.Severity.WARNING,
          AnnouncementService.Audience.ALL, now.minus(1, ChronoUnit.HOURS), null, true), "admin");
      service.create(command("Draft", AnnouncementService.Severity.INFO,
          AnnouncementService.Audience.ALL, now.minus(1, ChronoUnit.HOURS), null, false), "admin");
      service.create(command("Future", AnnouncementService.Severity.INFO,
          AnnouncementService.Audience.ALL, now.plus(2, ChronoUnit.HOURS), null, true), "admin");
      service.create(command("Expired", AnnouncementService.Severity.INFO,
          AnnouncementService.Audience.ALL, now.minus(5, ChronoUnit.HOURS),
          now.minus(1, ChronoUnit.HOURS), true), "admin");

      assertThat(service.active(AnnouncementService.Audience.AGENTS, now))
          .extracting(AnnouncementService.Announcement::title)
          .containsExactly("Live");
      assertThat(service.list()).hasSize(4);
      return null;
    });
  }

  @Test
  void the_audience_decides_who_sees_it_and_severity_decides_the_order() {
    OrganizationContext.runAs("announce-audience-" + UUID.randomUUID(), () -> {
      Instant now = Instant.now();
      Instant from = now.minus(1, ChronoUnit.HOURS);
      service.create(command("For everyone", AnnouncementService.Severity.INFO,
          AnnouncementService.Audience.ALL, from, null, true), "admin");
      service.create(command("Agents only", AnnouncementService.Severity.CRITICAL,
          AnnouncementService.Audience.AGENTS, from, null, true), "admin");
      service.create(command("Requesters only", AnnouncementService.Severity.WARNING,
          AnnouncementService.Audience.REQUESTERS, from, null, true), "admin");

      assertThat(service.active(AnnouncementService.Audience.AGENTS, now))
          .extracting(AnnouncementService.Announcement::title)
          .containsExactly("Agents only", "For everyone");
      assertThat(service.active(AnnouncementService.Audience.REQUESTERS, now))
          .extracting(AnnouncementService.Announcement::title)
          .containsExactly("Requesters only", "For everyone");
      return null;
    });
  }

  @Test
  void retiring_ends_the_broadcast_without_erasing_it() {
    OrganizationContext.runAs("announce-retire-" + UUID.randomUUID(), () -> {
      Instant now = Instant.now();
      AnnouncementService.Announcement created = service.create(command(
          "Payment outage", AnnouncementService.Severity.CRITICAL,
          AnnouncementService.Audience.ALL, now.minus(30, ChronoUnit.MINUTES), null, true), "admin");
      assertThat(service.active(AnnouncementService.Audience.AGENTS, now)).hasSize(1);

      AnnouncementService.Announcement retired = service.retire(created.id(), "admin");
      assertThat(retired.endsAt()).isNotNull();
      assertThat(service.active(AnnouncementService.Audience.AGENTS, Instant.now())).isEmpty();
      assertThat(service.list()).hasSize(1);
      return null;
    });
  }

  @Test
  void a_stale_edit_is_rejected_and_an_impossible_window_refused() {
    OrganizationContext.runAs("announce-invalid-" + UUID.randomUUID(), () -> {
      Instant now = Instant.now();
      AnnouncementService.Announcement created = service.create(command(
          "Maintenance", AnnouncementService.Severity.INFO, AnnouncementService.Audience.ALL,
          now, null, true), "admin");

      service.update(created.id(), created.version(), command(
          "Maintenance window moved", AnnouncementService.Severity.INFO,
          AnnouncementService.Audience.ALL, now, now.plus(3, ChronoUnit.HOURS), true), "admin");
      assertThatThrownBy(() -> service.update(created.id(), created.version(), command(
          "Stale", AnnouncementService.Severity.INFO, AnnouncementService.Audience.ALL,
          now, null, true), "admin"))
          .isInstanceOf(OptimisticLockingFailureException.class);

      assertThatThrownBy(() -> service.create(command(
          "Backwards", AnnouncementService.Severity.INFO, AnnouncementService.Audience.ALL,
          now, now.minus(1, ChronoUnit.HOURS), true), "admin"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("endsAt");
      return null;
    });
  }

  @Test
  void announcements_are_scoped_to_their_organization() {
    UUID id = OrganizationContext.runAs("announce-org-a", () -> service.create(command(
        "Org A only", AnnouncementService.Severity.INFO, AnnouncementService.Audience.ALL,
        Instant.now().minus(1, ChronoUnit.HOURS), null, true), "admin").id());

    assertThat(OrganizationContext.runAs("announce-org-b", () -> service.findById(id))).isEmpty();
    assertThat(OrganizationContext.runAs("announce-org-b",
        () -> service.active(AnnouncementService.Audience.AGENTS, Instant.now()))).isEmpty();
    assertThat(OrganizationContext.runAs("announce-org-a", () -> service.findById(id))).isPresent();
  }
}
