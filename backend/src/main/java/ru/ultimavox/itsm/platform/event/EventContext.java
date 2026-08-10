package ru.ultimavox.itsm.platform.event;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

final class EventContext {
  private static final List<String> ORGANIZATION_CLAIMS =
      List.of("organization_id", "org_id", "tenant_id");

  private EventContext() {}

  static String actorId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()
        || authentication.getName() == null || authentication.getName().isBlank()) {
      return "system";
    }
    return authentication.getName();
  }

  static String organizationId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
      for (String claim : ORGANIZATION_CLAIMS) {
        String value = jwt.getClaimAsString(claim);
        if (value != null && !value.isBlank()) {
          return value;
        }
      }
    }
    return "default";
  }
}
