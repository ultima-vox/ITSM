package ru.ultimavox.itsm.platform.authorization;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class FieldAccessControlIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
  static JdbcTemplate jdbc;
  static FieldAccessControl fields;

  @BeforeAll
  static void setup() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(dataSource).load().migrate();
    jdbc = new JdbcTemplate(dataSource);
    JdbcRbacRepository repository = new JdbcRbacRepository(jdbc, new ObjectMapper());
    fields = new FieldAccessControl(jdbc, new RbacPermissionChecker(repository));
  }

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    jdbc.update("DELETE FROM field_access_policy WHERE org_id LIKE 'field-test-%'");
    jdbc.update("DELETE FROM principal_role WHERE org_id LIKE 'field-test-%'");
  }

  @Test
  void enforcesPermissionAndTargetStateWhileLeavingUnconfiguredFieldsOpen() {
    String org = organization();
    assign(org, "agent", "SERVICE_DESK_AGENT");
    assign(org, "requester", "REQUESTER");

    assertThatCode(() -> fields.requireWrite(
        "agent", "work-item", "1", "resolutionNotes", "RESOLVED")).doesNotThrowAnyException();
    assertThatThrownBy(() -> fields.requireWrite(
        "requester", "work-item", "1", "resolutionNotes", "RESOLVED"))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> fields.requireWrite(
        "agent", "work-item", "1", "resolutionNotes", "IN_PROGRESS"))
        .isInstanceOf(AccessDeniedException.class);
    assertThatCode(() -> fields.requireWrite(
        "requester", "work-item", "1", "description", "IN_PROGRESS"))
        .doesNotThrowAnyException();
  }

  @Test
  void tenantPolicyOverridesGlobalPolicyWithoutLeakingAcrossOrganizations() {
    String org = organization();
    assign(org, "agent", "SERVICE_DESK_AGENT");
    jdbc.update("""
        INSERT INTO field_access_policy
          (org_id,object_type,field_key,operation,object_state,required_permission)
        VALUES (?,?,?,?,?,?)
        """, org, "work-item", "resolutionNotes", "WRITE", "RESOLVED", "admin.full");

    assertThatThrownBy(() -> fields.requireWrite(
        "agent", "work-item", "1", "resolutionNotes", "RESOLVED"))
        .isInstanceOf(AccessDeniedException.class);

    String other = organization();
    assign(other, "other-agent", "SERVICE_DESK_AGENT");
    assertThatCode(() -> fields.requireWrite(
        "other-agent", "work-item", "1", "resolutionNotes", "RESOLVED"))
        .doesNotThrowAnyException();
  }

  private static String organization() {
    String org = "field-test-" + UUID.randomUUID();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
        Jwt.withTokenValue("t").header("alg", "none").claim("sub", "admin")
            .claim("organization_id", org).build()));
    return org;
  }

  private static void assign(String org, String subject, String roleKey) {
    UUID role = jdbc.queryForObject("SELECT id FROM role WHERE role_key=?", UUID.class, roleKey);
    jdbc.update("INSERT INTO principal_role(org_id,subject_id,role_id) VALUES (?,?,?)", org, subject, role);
  }
}
