package ru.ultimavox.itsm.platform.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JdbcAutomationOrganizationTest {
  @AfterEach void clear() { SecurityContextHolder.clearContext(); }

  @Test void actionIdempotencyKeyIncludesTrustedOrganization() {
    JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    Mockito.when(jdbc.update(Mockito.anyString(), Mockito.<Object[]>any())).thenReturn(1);
    authenticate("org-red");
    new JdbcAutomationActionLogRepository(jdbc, new ObjectMapper())
        .tryLog("r", UUID.randomUUID(), "notify", "done", Map.of());
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).update(sql.capture(), args.capture());
    assertThat(sql.getValue()).contains("org_id, rule_key").contains("ON CONFLICT (org_id, rule_key");
    assertThat(args.getValue()[0]).isEqualTo("org-red");
  }

  @Test void toggleCreatesTenantOverrideWithoutMutatingDefaultRule() {
    JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    authenticate("org-red");
    UUID id = UUID.randomUUID();

    new JdbcAutomationRuleRepository(jdbc, new ObjectMapper()).setEnabled(id, false);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).query(sql.capture(), Mockito.any(RowMapper.class), args.capture());
    assertThat(sql.getValue())
        .contains("org_id IN (?, 'default')")
        .contains("INSERT INTO automation_rule (org_id")
        .contains("ON CONFLICT (org_id, rule_key)");
    assertThat(args.getValue()).containsExactly(id, "org-red", "org-red", "org-red", false);
  }

  private void authenticate(String org) {
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
        Jwt.withTokenValue("t").header("alg", "none").claim("sub", "u").claim("organization_id", org).build()));
  }
}
