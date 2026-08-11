package ru.ultimavox.itsm.platform.forms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JdbcFormDefinitionOrganizationTest {
  @AfterEach void clear() { SecurityContextHolder.clearContext(); }

  @Test void saveWritesTrustedOrganization() {
    JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    authenticate("org-red");
    FormDefinition form = new FormDefinition(null, "f", "work-item", 1, java.util.List.of());
    new JdbcFormDefinitionRepository(jdbc, new ObjectMapper()).save(form, "{}");
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).update(sql.capture(), args.capture());
    assertThat(sql.getValue()).contains("id, org_id, form_key");
    assertThat(args.getValue()[1]).isEqualTo("org-red");
  }

  private void authenticate(String org) {
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
        Jwt.withTokenValue("t").header("alg", "none").claim("sub", "u").claim("organization_id", org).build()));
  }
}
