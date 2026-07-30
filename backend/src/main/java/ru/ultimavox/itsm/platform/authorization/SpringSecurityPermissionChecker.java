package ru.ultimavox.itsm.platform.authorization;
import org.springframework.security.core.context.SecurityContextHolder; import org.springframework.stereotype.Component;
/** Conservative initial policy adapter: deny by default, allow explicit OAuth scope or Keycloak role. */
@Component class SpringSecurityPermissionChecker implements PermissionChecker {
 @Override public Decision check(Request request) { var authentication=SecurityContextHolder.getContext().getAuthentication(); if(authentication==null || !authentication.isAuthenticated() || !authentication.getName().equals(request.subject())) return new Decision(false,"anonymous-or-subject-mismatch"); String role="ROLE_itsm_"+request.permission().replaceAll("[^a-zA-Z0-9]","_"); String scope="SCOPE_itsm."+request.permission(); boolean allowed=authentication.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_itsm_admin")||a.getAuthority().equals(role)||a.getAuthority().equals(scope)); return new Decision(allowed,allowed?"keycloak-authority":"default-deny"); }
}
