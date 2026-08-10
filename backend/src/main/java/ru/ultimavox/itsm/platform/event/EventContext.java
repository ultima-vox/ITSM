package ru.ultimavox.itsm.platform.event;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

final class EventContext {
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
    return OrganizationContext.current();
  }
}
