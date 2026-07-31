package ru.ultimavox.itsm.platform.authorization;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

/**
 * Application-facing authorization facade. Controllers and engines call this before
 * reading or mutating domain data. Ownership and scope checks are explicit.
 */
@Component
public class AccessControl {

    private final PermissionChecker permissions;

    public AccessControl(PermissionChecker permissions) {
        this.permissions = permissions;
    }

    public void require(String subject, String permission, String objectType, String objectId) {
        PermissionChecker.Decision decision = permissions.check(
                new PermissionChecker.Request(subject, permission, objectType, objectId, null)
        );
        if (!decision.allowed()) {
            throw new AccessDeniedException("Permission denied: " + permission);
        }
    }

    /**
     * Requires permission and that the subject owns the object (subject equals ownerId),
     * or the subject has an elevated grant (admin-style permission already allowed by checker).
     * Ownership is an additional constraint when the base permission check succeeds via
     * a non-admin policy — callers should pass ownerId when known.
     */
    public void requireOwned(
            String subject,
            String permission,
            String objectType,
            String objectId,
            String ownerId
    ) {
        PermissionChecker.Decision decision = permissions.check(
                new PermissionChecker.Request(subject, permission, objectType, objectId, null)
        );
        if (!decision.allowed()) {
            throw new AccessDeniedException("Permission denied: " + permission);
        }
        // Admin / full grants skip ownership
        if (decision.policyId() != null
                && (decision.policyId().contains("admin") || decision.policyId().contains("ADMIN"))) {
            return;
        }
        if (ownerId != null && !Objects.equals(subject, ownerId)) {
            // Re-check with ownership-aware permission if subject is not owner
            PermissionChecker.Decision elevate = permissions.check(
                    new PermissionChecker.Request(subject, permission + ".any", objectType, objectId, null)
            );
            if (!elevate.allowed()) {
                // Still allow if subject has manager-style base permission without ownership restriction
                // when policy is role-based (not self-only). Self-only is indicated by policy ending in ownership.
                if (decision.policyId() != null && decision.policyId().endsWith("ownership")) {
                    throw new AccessDeniedException(
                            "Permission denied: ownership required for " + objectType + "/" + objectId);
                }
            }
        }
    }

    /**
     * Requires permission and that the subject holds at least one of the required scopes
     * (for example service or CI scope tags).
     */
    public void requireScope(
            String subject,
            String permission,
            String objectType,
            String objectId,
            Set<String> requiredScopes,
            Set<String> subjectScopes
    ) {
        require(subject, permission, objectType, objectId);
        if (requiredScopes == null || requiredScopes.isEmpty()) {
            return;
        }
        Set<String> held = subjectScopes == null ? Set.of() : subjectScopes;
        boolean overlap = requiredScopes.stream().anyMatch(held::contains);
        if (!overlap) {
            // Admin may still pass via elevated re-check
            PermissionChecker.Decision admin = permissions.check(
                    new PermissionChecker.Request(subject, "admin.full", objectType, objectId, null)
            );
            if (!admin.allowed()) {
                throw new AccessDeniedException(
                        "Permission denied: missing required scope for " + objectType);
            }
        }
    }

    public boolean isAllowed(String subject, String permission, String objectType, String objectId) {
        return permissions.check(
                new PermissionChecker.Request(subject, permission, objectType, objectId, null)
        ).allowed();
    }
}
