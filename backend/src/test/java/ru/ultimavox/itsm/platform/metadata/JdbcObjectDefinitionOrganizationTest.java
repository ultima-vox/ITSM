package ru.ultimavox.itsm.platform.metadata;

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

class JdbcObjectDefinitionOrganizationTest {
  @AfterEach void clear() { SecurityContextHolder.clearContext(); }

  @Test void lookupPrefersTenantAndAllowsOnlyDefaultFallback() {
    JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    authenticate("org-red");
    new JdbcObjectDefinitionRepository(jdbc, new ObjectMapper()).findActiveByKey("work-item");
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).query(sql.capture(), any(RowMapper.class), args.capture());
    assertThat(sql.getValue()).contains("org_id IN (?, 'default')").contains("ORDER BY (org_id = ?) DESC");
    assertThat(args.getValue()).containsExactly("org-red", "work-item", "org-red");
  }

  private void authenticate(String org) {
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
        Jwt.withTokenValue("t").header("alg", "none").claim("sub", "u").claim("organization_id", org).build()));
  }
}
