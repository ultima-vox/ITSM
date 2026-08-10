package ru.ultimavox.itsm.platform.authorization;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

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
     * or the subject has the explicit elevated {@code permission.any} grant.
     * Missing ownership data fails closed unless that elevated grant is present.
     */
    public void requireOwned(
            String subject,
            String permission,
            String objectType,
            String objectId,
            String ownerId
    ) {
        if (subject == null || subject.isBlank()) {
            throw new AccessDeniedException("Permission denied: authenticated subject required");
        }
        PermissionChecker.Decision decision = permissions.check(
                new PermissionChecker.Request(subject, permission, objectType, objectId, null)
        );
        if (!decision.allowed()) {
            throw new AccessDeniedException("Permission denied: " + permission);
        }
        if (ownerId != null && !ownerId.isBlank() && ownerId.equals(subject)) {
            return;
        }

        PermissionChecker.Decision elevated = permissions.check(
                new PermissionChecker.Request(subject, permission + ".any", objectType, objectId, null)
        );
        if (!elevated.allowed()) {
            throw new AccessDeniedException(
                    "Permission denied: ownership required for " + objectType + "/" + objectId);
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
