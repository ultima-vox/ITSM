package ru.ultimavox.itsm.platform.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Maps Keycloak realm roles to {@code ROLE_*} authorities and OAuth scopes to {@code SCOPE_*}.
 * Used by production {@link SecurityConfiguration}.
 */
final class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    Set<GrantedAuthority> authorities = new HashSet<>();

    Optional.ofNullable(jwt.getClaimAsString("scope")).stream()
        .flatMap(scope -> Arrays.stream(scope.split(" ")))
        .filter(s -> !s.isBlank())
        .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
        .forEach(authorities::add);

    Map<String, Object> realm = jwt.getClaimAsMap("realm_access");
    if (realm != null && realm.get("roles") instanceof Collection<?> roles) {
      roles.stream()
          .filter(String.class::isInstance)
          .map(String.class::cast)
          .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
          .collect(Collectors.toCollection(() -> authorities));
    }

    String subject = jwt.getSubject();
    if (subject == null || subject.isBlank()) {
      throw new InvalidBearerTokenException(
          "Access token has no 'sub' claim; the identity provider client must include the "
              + "'basic' client scope");
    }
    return new JwtAuthenticationToken(jwt, authorities, subject);
  }
}
