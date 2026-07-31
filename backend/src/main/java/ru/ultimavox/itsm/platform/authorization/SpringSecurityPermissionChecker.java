package ru.ultimavox.itsm.platform.authorization;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Conservative JWT authority adapter: deny by default, allow explicit OAuth scope or Keycloak role.
 * Used as one vote inside {@link CompositePermissionChecker}.
 */
@Component
class SpringSecurityPermissionChecker implements AuthorityPermissionChecker {

    @Override
    public Decision check(Request request) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Decision.deny("anonymous");
        }
        if (request.subject() != null && !authentication.getName().equals(request.subject())) {
            return Decision.deny("subject-mismatch");
        }

        String role = "ROLE_itsm_" + request.permission().replaceAll("[^a-zA-Z0-9]", "_");
        String scope = "SCOPE_itsm." + request.permission();
        boolean allowed = authentication.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_itsm_admin")
                        || a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals(role)
                        || a.getAuthority().equals(scope)
        );
        return allowed ? Decision.allow("keycloak-authority") : Decision.deny("default-deny");
    }
}
