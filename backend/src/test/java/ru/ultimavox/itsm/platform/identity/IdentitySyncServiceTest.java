package ru.ultimavox.itsm.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

@Testcontainers(disabledWithoutDocker = true)
class IdentitySyncServiceTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  static JdbcTemplate jdbc;
  static IdentitySyncService service;

  @BeforeAll
  static void setup() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(dataSource).load().migrate();
    jdbc = new JdbcTemplate(dataSource);
    service = new IdentitySyncService(jdbc);
  }

  @Test
  void mappingGrantsRequesterFromItsmUsersGroup() {
    String subject = "sync-" + UUID.randomUUID();
    Jwt jwt = jwt(subject, List.of("ITSM-Users"), List.of());

    OrganizationContext.runAs("default", () -> {
      service.sync(jwt);
      service.sync(jwt);
      assertThat(roles(subject)).containsExactly("REQUESTER");
      assertThat(accountEnabled("http://localhost/realms/itsm", subject)).isTrue();
      return null;
    });
  }

  @Test
  void mappingGrantsRequesterFromRealmRoleAndGroupPath() {
    String subject = "sync-path-" + UUID.randomUUID();
    Jwt jwt = jwt(subject, List.of("/Org/ITSM-Users"), List.of("ITSM-Users"));

    OrganizationContext.runAs("default", () -> {
      service.sync(jwt);
      assertThat(roles(subject)).containsExactly("REQUESTER");
      return null;
    });
  }

  @ParameterizedTest
  @CsvSource({
      "ITSM-Users, REQUESTER",
      "ITSM-ServiceDesk, SERVICE_DESK_AGENT",
      "ITSM-ServiceDesk-Managers, SERVICE_DESK_MANAGER",
      "ITSM-Change-Managers, CHANGE_MANAGER",
      "ITSM-CAB, CAB_MEMBER",
      "ITSM-Admins, ADMIN"
  })
  void mappingGrantsSeededIdpGroup(String group, String role) {
    String subject = "sync-map-" + UUID.randomUUID();
    Jwt jwt = jwt(subject, List.of(group), List.of());

    OrganizationContext.runAs("default", () -> {
      service.sync(jwt);
      assertThat(roles(subject)).containsExactly(role);
      return null;
    });
  }

  @Test
  void renameKeepsSingleAccountForSameSubject() {
    String subject = "objectguid-" + UUID.randomUUID();
    Jwt first = jwt(subject, List.of("ITSM-Users"), List.of());
    Jwt renamed = Jwt.withTokenValue("token")
        .header("alg", "none")
        .issuer("http://localhost/realms/itsm")
        .subject(subject)
        .audience(List.of("itsm-backend"))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .claim("groups", List.of("ITSM-Users"))
        .claim("preferred_username", "renamed-account")
        .claim("realm_access", Map.of("roles", List.of()))
        .build();

    OrganizationContext.runAs("default", () -> {
      service.sync(first);
      service.sync(renamed);
      assertThat(roles(subject)).containsExactly("REQUESTER");
      Number accounts = jdbc.queryForObject(
          "SELECT count(*) FROM identity_account WHERE idp = ? AND external_id = ?",
          Number.class,
          "http://localhost/realms/itsm",
          subject
      );
      assertThat(accounts).isNotNull();
      assertThat(accounts.intValue()).isEqualTo(1);
      return null;
    });
  }

  @Test
  void unknownGroupDoesNotGrantAdmin() {
    String subject = "sync-unknown-" + UUID.randomUUID();
    Jwt jwt = jwt(subject, List.of("random-group", "ADMIN", "itsm_admin"), List.of("ADMIN"));

    OrganizationContext.runAs("default", () -> {
      service.sync(jwt);
      assertThat(roles(subject)).isEmpty();
      assertThat(accountEnabled("http://localhost/realms/itsm", subject)).isTrue();
      return null;
    });
  }

  @Test
  void disabledIdentityAccountCannotAuthenticate() {
    String subject = "sync-disabled-" + UUID.randomUUID();
    jdbc.update(
        """
            INSERT INTO identity_account (idp, external_id, subject_id, enabled, last_sync)
            VALUES (?, ?, ?, FALSE, now())
            """,
        "http://localhost/realms/itsm", subject, subject
    );
    Jwt jwt = jwt(subject, List.of("ITSM-Users", "ITSM-Admins"), List.of());

    OrganizationContext.runAs("default", () -> {
      assertThatThrownBy(() -> service.sync(jwt))
          .isInstanceOf(DisabledException.class)
          .hasMessageContaining("disabled");
      assertThat(roles(subject)).isEmpty();
      return null;
    });
  }

  private static Jwt jwt(String subject, List<String> groups, List<String> realmRoles) {
    return Jwt.withTokenValue("token")
        .header("alg", "none")
        .issuer("http://localhost/realms/itsm")
        .subject(subject)
        .audience(List.of("itsm-backend"))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .claim("groups", groups)
        .claim("realm_access", Map.of("roles", realmRoles))
        .build();
  }

  private static List<String> roles(String subject) {
    return jdbc.query(
        """
            SELECT r.role_key
            FROM principal_role pr
            JOIN role r ON r.id = pr.role_id
            WHERE pr.org_id = ? AND pr.subject_id = ?
            ORDER BY r.role_key
            """,
        (rs, i) -> rs.getString(1),
        OrganizationContext.current(), subject
    );
  }

  private static boolean accountEnabled(String idp, String externalId) {
    Boolean enabled = jdbc.queryForObject(
        "SELECT enabled FROM identity_account WHERE idp = ? AND external_id = ?",
        Boolean.class,
        idp, externalId
    );
    return Boolean.TRUE.equals(enabled);
  }
}
