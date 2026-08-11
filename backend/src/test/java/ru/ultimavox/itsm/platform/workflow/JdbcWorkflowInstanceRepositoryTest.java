package ru.ultimavox.itsm.platform.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JdbcWorkflowInstanceRepositoryTest {
  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void lookupIncludesTrustedOrganizationPredicate() {
    JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
        Jwt.withTokenValue("token").header("alg", "none")
            .claim("sub", "operator").claim("organization_id", "org-green").build()
    ));

    new JdbcWorkflowInstanceRepository(jdbc).findByObject("change", UUID.randomUUID().toString());

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).query(sql.capture(), any(RowMapper.class), args.capture());
    assertThat(sql.getValue()).contains("org_id = ?");
    assertThat(args.getValue()[0]).isEqualTo("org-green");
  }

  @Test
  void definitionMigrationUsesTenantAndOptimisticVersionPredicates() {
    JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    Mockito.when(jdbc.update(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any(),
        Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(1);
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
        Jwt.withTokenValue("token").header("alg", "none")
            .claim("sub", "operator").claim("organization_id", "org-green").build()));
    WorkflowInstance instance = new WorkflowInstance(UUID.randomUUID(), "change", "42",
        "SUBMITTED", 1, 8, Instant.now());

    WorkflowInstance migrated = new JdbcWorkflowInstanceRepository(jdbc)
        .updateDefinitionVersion(instance, 2, 8);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).update(sql.capture(), args.capture());
    assertThat(sql.getValue()).contains("org_id=?").contains("version=?").contains("definition_version=?");
    assertThat(args.getValue()[0]).isEqualTo(2);
    assertThat(args.getValue()[3]).isEqualTo("org-green");
    assertThat(args.getValue()[4]).isEqualTo(8);
    assertThat(args.getValue()[5]).isEqualTo(1);
    assertThat(migrated.definitionVersion()).isEqualTo(2);
    assertThat(migrated.version()).isEqualTo(9);
  }
}
