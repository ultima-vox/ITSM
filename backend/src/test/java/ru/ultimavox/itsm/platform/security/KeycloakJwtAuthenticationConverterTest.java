package ru.ultimavox.itsm.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakJwtAuthenticationConverterTest {

  private final KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter();

  @Test
  void maps_realm_roles_and_scopes() {
    Jwt jwt = Jwt.withTokenValue("token")
        .header("alg", "none")
        .subject("user-1")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
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
}
