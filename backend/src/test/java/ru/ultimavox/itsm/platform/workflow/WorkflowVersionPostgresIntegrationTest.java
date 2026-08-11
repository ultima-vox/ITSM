package ru.ultimavox.itsm.platform.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.PermissionChecker;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

@Testcontainers
class WorkflowVersionPostgresIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
  static JdbcTemplate jdbc;
  static WorkflowEngine engine;
  static String organization;

  @BeforeAll
  static void setup() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(dataSource).load().migrate();
    jdbc = new JdbcTemplate(dataSource);
    organization = "workflow-version-" + UUID.randomUUID();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
        Jwt.withTokenValue("t").header("alg", "none").claim("sub", "admin")
            .claim("organization_id", organization).build()));
    engine = new WorkflowEngine(
        new JdbcWorkflowDefinitionRepository(jdbc, new ObjectMapper()),
        new JdbcWorkflowInstanceRepository(jdbc),
        request -> PermissionChecker.Decision.allow("test"),
        mock(AuditTrail.class), mock(IntegrationEventOutbox.class));
  }

  @AfterAll
  static void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void persistsCompatibleMigrationAndOptimisticVersion() {
    String objectId = UUID.randomUUID().toString();
    String definition = """
        {"initialState":"NEW","states":["NEW","IN_PROGRESS","DONE"],"transitions":[]}
        """;
    jdbc.update("""
        INSERT INTO workflow_definition(org_id,object_key,version,active,definition)
        VALUES (?, 'migration-test', 1, false, ?::jsonb), (?, 'migration-test', 2, true, ?::jsonb)
        """, organization, definition, organization, definition);
    UUID instanceId = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO workflow_instance
          (id,org_id,object_type,object_id,state,definition_version,version,updated_at)
        VALUES (?,?,?,?,?,?,?,?)
        """, instanceId, organization, "migration-test", objectId, "IN_PROGRESS", 1, 3,
        Timestamp.from(Instant.now()));

    WorkflowInstance migrated = engine.migrateInstance(new WorkflowEngine.MigrationCommand(
        "admin", "migration-test", objectId, 2, 3, UUID.randomUUID()));
    WorkflowInstance persisted = engine.findInstance("migration-test", objectId).orElseThrow();

    assertThat(migrated.definitionVersion()).isEqualTo(2);
    assertThat(migrated.version()).isEqualTo(4);
    assertThat(persisted.state()).isEqualTo("IN_PROGRESS");
    assertThat(persisted.definitionVersion()).isEqualTo(2);
    assertThat(persisted.version()).isEqualTo(4);
  }
}
