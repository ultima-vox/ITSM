package ru.ultimavox.itsm.platform.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
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
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

@Testcontainers(disabledWithoutDocker = true)
class RoleDelegationIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  static JdbcTemplate jdbc;
  static RoleDelegationService service;
  static JdbcRbacRepository repository;

  @BeforeAll
  static void setup() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(dataSource).load().migrate();
    jdbc = new JdbcTemplate(dataSource);
    service = new RoleDelegationService(jdbc, mock(AuditTrail.class), mock(IntegrationEventOutbox.class));
    repository = new JdbcRbacRepository(jdbc, new ObjectMapper());
  }

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    jdbc.update("DELETE FROM role_delegation");
    jdbc.update("DELETE FROM principal_role WHERE org_id LIKE 'delegation-test-%'");
  }

  @Test
  void activeDelegationGrantsRolePermissionsAndRevocationRemovesThem() {
    String org = organization();
    assign(org, "manager", "SERVICE_DESK_MANAGER");
    Instant now = Instant.now();

    RoleDelegationService.Delegation delegation = service.create(
        new RoleDelegationService.Command("manager", "stand-in", "SERVICE_DESK_MANAGER",
            now, now.plus(2, ChronoUnit.DAYS), "vacation cover"), "admin");

    assertThat(repository.rolesForSubject("stand-in")).contains("SERVICE_DESK_MANAGER");
    assertThat(repository.hasPermission("stand-in", "work-item.read.any")).isTrue();

    service.revoke(delegation.id(), "admin");
    assertThat(repository.rolesForSubject("stand-in")).doesNotContain("SERVICE_DESK_MANAGER");
    assertThat(repository.hasPermission("stand-in", "work-item.read.any")).isFalse();
  }

  @Test
  void futureExpiredAndOtherTenantDelegationsNeverGrantPermission() {
    String org = organization();
    UUID role = role("SERVICE_DESK_AGENT");
    Instant now = Instant.now();
    insert(org, "future-user", role, now.plus(1, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS));
    insert(org, "expired-user", role, now.minus(2, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS));
    insert("other-tenant", "foreign-user", role, now.minusSeconds(10), now.plusSeconds(600));

    assertThat(repository.hasPermission("future-user", "work-item.read")).isFalse();
    assertThat(repository.hasPermission("expired-user", "work-item.read")).isFalse();
    assertThat(repository.hasPermission("foreign-user", "work-item.read")).isFalse();
  }

  @Test
  void blocksSelfDelegationPrivilegedRoleAndDelegationChaining() {
    String org = organization();
    assign(org, "admin-owner", "ADMIN");
    assign(org, "agent", "SERVICE_DESK_AGENT");
    Instant now = Instant.now();

    assertThatThrownBy(() -> service.create(command("agent", "agent", "SERVICE_DESK_AGENT", now), "admin"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Self-delegation");
    assertThatThrownBy(() -> service.create(command("admin-owner", "delegate", "ADMIN", now), "admin"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("delegable role");

    service.create(command("agent", "first", "SERVICE_DESK_AGENT", now), "admin");
    assertThatThrownBy(() -> service.create(command("first", "second", "SERVICE_DESK_AGENT", now), "admin"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("directly hold");
  }

  private static String organization() {
    String org = "delegation-test-" + UUID.randomUUID();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
        Jwt.withTokenValue("t").header("alg", "none").claim("sub", "admin")
            .claim("organization_id", org).build()));
    return org;
  }

  private static void assign(String org, String subject, String roleKey) {
    jdbc.update("INSERT INTO principal_role(org_id,subject_id,role_id) VALUES (?,?,?)",
        org, subject, role(roleKey));
  }

  private static UUID role(String key) {
    return jdbc.queryForObject("SELECT id FROM role WHERE role_key=?", UUID.class, key);
  }

  private static RoleDelegationService.Command command(
      String from, String to, String roleKey, Instant now) {
    return new RoleDelegationService.Command(from, to, roleKey, now,
        now.plus(1, ChronoUnit.DAYS), "coverage");
  }

  private static void insert(String org, String delegatee, UUID role, Instant start, Instant end) {
    jdbc.update("""
        INSERT INTO role_delegation
          (org_id,delegator_id,delegatee_id,role_id,starts_at,expires_at,reason,created_by)
        VALUES (?,?,?,?,?,?,?,?)
        """, org, "source", delegatee, role, Timestamp.from(start), Timestamp.from(end), "test", "admin");
  }
}
