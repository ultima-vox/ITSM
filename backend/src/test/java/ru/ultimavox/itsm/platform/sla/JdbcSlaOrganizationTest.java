package ru.ultimavox.itsm.platform.sla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
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

class JdbcSlaOrganizationTest {
  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void clockLookupIncludesTrustedOrganizationPredicate() {
    JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    authenticate("org-blue");

    new JdbcSlaClockRepository(jdbc).findById(UUID.randomUUID());

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).query(sql.capture(), any(RowMapper.class), args.capture());
    assertThat(sql.getValue()).contains("id = ? AND org_id = ?");
    assertThat(args.getValue()[1]).isEqualTo("org-blue");
  }

  @Test
  void policyLookupIncludesTrustedOrganizationPredicate() {
    JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    authenticate("org-blue");

    new JdbcSlaPolicyRepository(jdbc, new ObjectMapper()).findByKey("p1");

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).query(sql.capture(), any(RowMapper.class), args.capture());
    assertThat(sql.getValue()).contains("org_id IN (?, 'default') AND policy_key = ?");
    assertThat(args.getValue()).containsExactly("org-blue", "p1", "org-blue");
  }

  private void authenticate(String organization) {
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
        Jwt.withTokenValue("token").header("alg", "none")
            .claim("sub", "operator").claim("organization_id", organization).build()
    ));
  }
}
