package ru.ultimavox.itsm.platform.notification;

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

class JdbcNotificationStoreOrganizationTest {
  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void recipientLookupIncludesTrustedOrganizationPredicate() {
    JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    authenticate("org-green");

    new JdbcNotificationStore(jdbc, new ObjectMapper())
        .listForRecipient("operator", 20, 0, false);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).query(sql.capture(), any(RowMapper.class), args.capture());
    assertThat(sql.getValue()).contains("org_id = ? AND recipient_subject = ?");
    assertThat(args.getValue()).startsWith("org-green", "operator");
  }

  private void authenticate(String organization) {
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
        Jwt.withTokenValue("token").header("alg", "none")
            .claim("sub", "operator").claim("organization_id", organization).build()
    ));
  }
}
