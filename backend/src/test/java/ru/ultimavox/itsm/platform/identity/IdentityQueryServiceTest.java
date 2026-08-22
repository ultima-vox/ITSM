package ru.ultimavox.itsm.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

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
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.identity.IdentityQueryService.GroupRoleMappingRecord;
import ru.ultimavox.itsm.platform.identity.IdentityQueryService.IdentityAccountRecord;

@Testcontainers(disabledWithoutDocker = true)
class IdentityQueryServiceTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  static JdbcTemplate jdbc;
  static IdentityQueryService query;

  @BeforeAll
  static void setup() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(dataSource).load().migrate();
    jdbc = new JdbcTemplate(dataSource);
    query = new IdentityQueryService(jdbc);
  }

  @Test
  void listGroupMappingsReturnsSeededIdpGroups() {
    List<GroupRoleMappingRecord> mappings = query.listGroupMappings(0, 200);
    assertThat(mappings)
        .extracting(GroupRoleMappingRecord::idpGroup)
        .contains(
            "ITSM-Users",
            "ITSM-ServiceDesk",
            "ITSM-ServiceDesk-Managers",
            "ITSM-Change-Managers",
            "ITSM-CAB",
            "ITSM-Admins"
        );
    assertThat(mappings)
        .filteredOn(row -> "ITSM-Users".equals(row.idpGroup()))
        .extracting(GroupRoleMappingRecord::roleName)
        .containsExactly("REQUESTER");
    assertThat(mappings)
        .filteredOn(row -> "ITSM-Admins".equals(row.idpGroup()))
        .extracting(GroupRoleMappingRecord::roleName)
        .containsExactly("ADMIN");
  }

  @Test
  void listGroupMappingsRespectsPageSize() {
    List<GroupRoleMappingRecord> first = query.listGroupMappings(0, 2);
    List<GroupRoleMappingRecord> second = query.listGroupMappings(1, 2);
    assertThat(first).hasSize(2);
    assertThat(second).hasSize(2);
    assertThat(first.get(0).idpGroup()).isNotEqualTo(second.get(0).idpGroup());
  }

  @Test
  void listAccountsIncludesRolesForCurrentOrganizationOnly() {
    String subject = "query-" + UUID.randomUUID();
    insertAccount("http://localhost/realms/itsm", subject, true);
    UUID requester = roleId("REQUESTER");
    UUID admin = roleId("ADMIN");
    jdbc.update(
        "INSERT INTO principal_role (org_id, subject_id, role_id) VALUES (?, ?, ?), (?, ?, ?)",
        "org-a", subject, requester,
        "org-b", subject, admin
    );

    List<IdentityAccountRecord> inA = OrganizationContext.runAs("org-a", () -> query.listAccounts(0, 200));
    IdentityAccountRecord accountA = account(inA, subject);
    assertThat(accountA.idp()).isEqualTo("http://localhost/realms/itsm");
    assertThat(accountA.externalId()).isEqualTo(subject);
    assertThat(accountA.subjectId()).isEqualTo(subject);
    assertThat(accountA.enabled()).isTrue();
    assertThat(accountA.lastSync()).isNotNull();
    assertThat(accountA.roleKeys()).containsExactly("REQUESTER");

    List<IdentityAccountRecord> inB = OrganizationContext.runAs("org-b", () -> query.listAccounts(0, 200));
    assertThat(account(inB, subject).roleKeys()).containsExactly("ADMIN");
  }

  @Test
  void listAccountsKeepsDisabledRowsAndEmptyRoleKeys() {
    String subject = "query-disabled-" + UUID.randomUUID();
    insertAccount("http://localhost/realms/itsm", subject, false);

    List<IdentityAccountRecord> rows = OrganizationContext.runAs("default", () -> query.listAccounts(0, 200));
    IdentityAccountRecord account = account(rows, subject);
    assertThat(account.enabled()).isFalse();
    assertThat(account.roleKeys()).isEmpty();
  }

  @Test
  void listAccountsCapsPageSizeAndPaginates() {
    String prefix = "query-page-" + UUID.randomUUID() + "-";
    insertAccount("http://localhost/realms/itsm", prefix + "z", true);
    insertAccount("http://localhost/realms/itsm", prefix + "a", true);
    insertAccount("http://localhost/realms/itsm", prefix + "m", true);

    List<IdentityAccountRecord> page = OrganizationContext.runAs("default", () -> query.listAccounts(0, 1));
    assertThat(page).hasSize(1);

    List<IdentityAccountRecord> oversized = OrganizationContext.runAs("default", () -> query.listAccounts(0, 5000));
    assertThat(oversized.size()).isLessThanOrEqualTo(IdentityQueryService.MAX_PAGE_SIZE);
    assertThat(oversized)
        .extracting(IdentityAccountRecord::subjectId)
        .contains(prefix + "a", prefix + "m", prefix + "z");
  }

  private static void insertAccount(String idp, String subject, boolean enabled) {
    jdbc.update(
        """
            INSERT INTO identity_account (idp, external_id, subject_id, enabled, last_sync)
            VALUES (?, ?, ?, ?, now())
            """,
        idp, subject, subject, enabled
    );
  }

  private static UUID roleId(String roleKey) {
    return jdbc.queryForObject("SELECT id FROM role WHERE role_key = ?", UUID.class, roleKey);
  }

  private static IdentityAccountRecord account(List<IdentityAccountRecord> rows, String subject) {
    return rows.stream()
        .filter(row -> subject.equals(row.subjectId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing identity_account for " + subject));
  }
}
