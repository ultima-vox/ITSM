package ru.ultimavox.itsm.servicedesk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

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
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

@Testcontainers(disabledWithoutDocker = true)
class QueueSavedViewServiceTest {
  @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
  static QueueSavedViewService service;

  @BeforeAll
  static void setup() {
    var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(ds).load().migrate();
    service = new QueueSavedViewService(
        new JdbcTemplate(ds), mock(AuditTrail.class), mock(IntegrationEventOutbox.class));
  }

  @Test
  void persistsPerOwnerAndRejectsDuplicateName() {
    OrganizationContext.runAs("queue-views-" + UUID.randomUUID(), () -> {
      var command = new QueueSavedViewService.Command(
          "Breached mine", "breached", "critical", "incident", "", "breached");
      QueueSavedViewService.SavedView created = service.create("alice", command);
      assertThat(created.id()).isNotNull();
      assertThat(service.list("alice")).extracting(QueueSavedViewService.SavedView::name)
          .containsExactly("Breached mine");
      assertThat(service.list("bob")).isEmpty();
      assertThatThrownBy(() -> service.create("alice", command))
          .isInstanceOf(QueueSavedViewService.DuplicateNameException.class);
      service.delete("alice", created.id());
      assertThat(service.list("alice")).isEmpty();
      assertThatThrownBy(() -> service.delete("alice", created.id()))
          .isInstanceOf(QueueSavedViewService.NotFoundException.class);
      return null;
    });
  }

  @Test
  void rejectsUnknownFilterValues() {
    OrganizationContext.runAs("queue-views-" + UUID.randomUUID(), () -> {
      assertThatThrownBy(() -> service.create(
          "alice", new QueueSavedViewService.Command("Bad", "nope", "", "", "", "")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("tab");
      return null;
    });
  }
}
