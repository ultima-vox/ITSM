package ru.ultimavox.itsm.cmdb.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import ru.ultimavox.itsm.cmdb.domain.CiRelationship;
import ru.ultimavox.itsm.cmdb.domain.ConfigurationItem;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

@Testcontainers
class CmdbRelationshipIntegrationTest {
  @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
  static CmdbCommands commands;

  @BeforeAll
  static void setup() {
    var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(ds).load().migrate();
    var jdbc = new JdbcTemplate(ds);
    var json = new ObjectMapper();
    commands = new CmdbCommands(jdbc, new CmdbQuery(jdbc, json), mock(AuditTrail.class),
        mock(IntegrationEventOutbox.class), json);
  }

  @Test
  void updatesRelationshipTypeInsideOrganization() {
    OrganizationContext.runAs("cmdb-a", () -> {
      var source = createCi("source");
      var target = createCi("target");
      var relation = commands.createRelationship(source.id(), target.id(), CiRelationship.Type.DEPENDS_ON, "alice");

      var updated = commands.updateRelationship(relation.id(), CiRelationship.Type.RUNS_ON, "alice");

      assertThat(updated.type()).isEqualTo(CiRelationship.Type.RUNS_ON);
      assertThat(commands.updateRelationship(relation.id(), CiRelationship.Type.RUNS_ON, "alice"))
          .isEqualTo(updated);
      return null;
    });
  }

  @Test
  void cannotUpdateRelationshipFromAnotherOrganization() {
    UUID relationId = OrganizationContext.runAs("cmdb-owner", () -> {
      var source = createCi("source");
      var target = createCi("target");
      return commands.createRelationship(source.id(), target.id(), CiRelationship.Type.USES, "alice").id();
    });

    assertThatThrownBy(() -> OrganizationContext.runAs("cmdb-other", () ->
        commands.updateRelationship(relationId, CiRelationship.Type.CONNECTED_TO, "mallory")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Relationship not found");
  }

  private static ConfigurationItem createCi(String name) {
    return commands.create(new CmdbCommands.CreateCommand(
        name + "-" + UUID.randomUUID(), "service", ConfigurationItem.Status.OPERATIONAL, null, Map.of()), "alice");
  }
}
