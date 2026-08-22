package ru.ultimavox.itsm.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class KeycloakJwtAuthenticationConverterTest {

  private final KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter();

  @Test
  void maps_realm_roles_and_scopes() {
    Jwt jwt = baseJwt()
        .subject("user-1")
        .audience(List.of("itsm-backend"))
        .claim("scope", "openid itsm.work-item.read")
        .claim("realm_access", Map.of("roles", List.of("itsm_admin", "SERVICE_DESK_AGENT")))
        .build();

    var auth = converter.convert(jwt);
    assertThat(auth).isNotNull();
    assertThat(auth.getName()).isEqualTo("user-1");
    assertThat(auth.getAuthorities().stream().map(GrantedAuthority::getAuthority))
        .contains(
            "SCOPE_openid",
            "SCOPE_itsm.work-item.read",
            "ROLE_itsm_admin",
            "ROLE_SERVICE_DESK_AGENT"
        );
  }

  @Test
  void accepts_token_when_aud_contains_configured_audience() {
    Jwt jwt = baseJwt()
        .subject("user-1")
        .audience(List.of("itsm-spa", "itsm-backend"))
        .build();

    var auth = converter.convert(jwt);
    assertThat(auth).isNotNull();
    assertThat(auth.getName()).isEqualTo("user-1");
  }

  @Test
  void rejects_token_with_missing_aud() {
    Jwt jwt = baseJwt().subject("user-1").build();

    assertThatThrownBy(() -> converter.convert(jwt))
        .isInstanceOf(InvalidBearerTokenException.class)
        .hasMessageContaining("audience");
  }

  @Test
  void rejects_token_with_wrong_aud() {
    Jwt jwt = baseJwt()
        .subject("user-1")
        .audience(List.of("other-api"))
        .build();

    assertThatThrownBy(() -> converter.convert(jwt))
        .isInstanceOf(InvalidBearerTokenException.class)
        .hasMessageContaining("audience");
  }

  @Test
  void rejects_token_with_missing_sub() {
    Jwt jwt = baseJwt()
        .audience(List.of("itsm-backend"))
        .claim("preferred_username", "nobody")
        .build();

    assertThatThrownBy(() -> converter.convert(jwt))
        .isInstanceOf(InvalidBearerTokenException.class)
        .hasMessageContaining("'sub'");
  }

  private static Jwt.Builder baseJwt() {
    return Jwt.withTokenValue("token")
        .header("alg", "none")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600));
  }
}
