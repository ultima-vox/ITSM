package ru.ultimavox.itsm.platform.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JdbcRbacRepositoryOrganizationTest {
  @AfterEach void clear() { SecurityContextHolder.clearContext(); }

  @Test void permissionDecisionScopesEveryGrantSourceToTrustedOrganization() {
    JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
        Jwt.withTokenValue("t").header("alg", "none").claim("sub", "u")
            .claim("organization_id", "org-secure").build()));

    new JdbcRbacRepository(jdbc, new ObjectMapper()).permissionsForSubject("same-user");

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).query(sql.capture(), any(RowMapper.class), args.capture());
    assertThat(sql.getValue()).contains("pr.org_id = ?").contains("g.org_id = ?");
    assertThat(args.getValue()).containsExactly("org-secure", "same-user", "org-secure", "same-user");
  }
}
