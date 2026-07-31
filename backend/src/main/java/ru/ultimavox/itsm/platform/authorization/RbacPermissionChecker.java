package ru.ultimavox.itsm.platform.authorization;

import org.springframework.stereotype.Component;

/** Persisted RBAC adapter: principal roles and direct grants. Deny by default. */
@Component
class RbacPermissionChecker implements AuthorityPermissionChecker {

    private final RbacRepository rbac;

    RbacPermissionChecker(RbacRepository rbac) {
        this.rbac = rbac;
    }

    @Override
    public Decision check(Request request) {
        if (request.subject() == null || request.subject().isBlank()) {
            return Decision.deny("no-subject");
        }
        if (rbac.hasPermission(request.subject(), request.permission())) {
            SetOfRoles roles = new SetOfRoles(rbac.rolesForSubject(request.subject()));
            if (roles.contains("ADMIN")) {
                return Decision.allow("rbac-ADMIN");
            }
            return Decision.allow("rbac-role");
        }
        return Decision.deny("rbac-deny");
    }

    /** Tiny helper to keep stream-free readability. */
    private record SetOfRoles(java.util.Set<String> roles) {
        boolean contains(String role) {
            return roles.contains(role);
        }
    }
}
