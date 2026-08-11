package ru.ultimavox.itsm.platform.authorization;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Resolves organization scope from trusted authenticated JWT claims. */
public final class OrganizationContext {
  private static final List<String> CLAIMS = List.of("organization_id", "org_id", "tenant_id");
  private static final ThreadLocal<String> SYSTEM_OVERRIDE = new ThreadLocal<>();

  private OrganizationContext() {}

  public static String current() {
    String override = SYSTEM_OVERRIDE.get();
    if (override != null) return override;
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

  /** Runs trusted background work in an explicit tenant scope. Never expose to request input. */
  public static <T> T runAs(String organizationId, Supplier<T> action) {
    Objects.requireNonNull(action, "action");
    String normalized = Objects.requireNonNull(organizationId, "organizationId").trim();
    if (normalized.isEmpty()) throw new IllegalArgumentException("organizationId is required");
    String previous = SYSTEM_OVERRIDE.get();
    SYSTEM_OVERRIDE.set(normalized);
    try {
      return action.get();
    } finally {
      if (previous == null) SYSTEM_OVERRIDE.remove(); else SYSTEM_OVERRIDE.set(previous);
    }
  }
}
