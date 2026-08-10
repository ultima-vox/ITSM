package ru.ultimavox.itsm.platform.authorization;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Resolves organization scope from trusted authenticated JWT claims. */
public final class OrganizationContext {
  private static final List<String> CLAIMS = List.of("organization_id", "org_id", "tenant_id");

  private OrganizationContext() {}

  public static String current() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
      for (String claim : CLAIMS) {
        String value = jwt.getClaimAsString(claim);
        if (value != null && !value.isBlank()) {
          return value;
        }
      }
    }
    return "default";
  }
}
