package ru.ultimavox.itsm.platform.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccessControlTest {

    @Test
    void ownerMayUseBasePermission() {
        AccessControl access = accessWith("work-item.update");
        assertDoesNotThrow(() -> access.requireOwned(
                "alice", "work-item.update", "work-item", "INC-1", "alice"));
    }

    @Test
    void nonOwnerNeedsExplicitAnyPermissionRegardlessOfPolicyName() {
        AccessControl access = accessWith("work-item.update");
        assertThrows(AccessDeniedException.class, () -> access.requireOwned(
                "alice", "work-item.update", "work-item", "INC-1", "bob"));
    }

    @Test
    void nonOwnerWithExplicitAnyPermissionIsAllowed() {
        AccessControl access = accessWith("work-item.update", "work-item.update.any");
        assertDoesNotThrow(() -> access.requireOwned(
                "alice", "work-item.update", "work-item", "INC-1", "bob"));
    }

    @Test
    void missingOwnerFailsClosedWithoutExplicitAnyPermission() {
        AccessControl access = accessWith("work-item.update");
        assertThrows(AccessDeniedException.class, () -> access.requireOwned(
                "alice", "work-item.update", "work-item", "INC-1", null));
    }

    @Test
    void blankSubjectFailsClosedEvenWhenOwnerIsBlank() {
        AccessControl access = accessWith("work-item.update");
        assertThrows(AccessDeniedException.class, () -> access.requireOwned(
                "", "work-item.update", "work-item", "INC-1", ""));
    }

    private static AccessControl accessWith(String... allowedPermissions) {
        Set<String> allowed = new HashSet<>(Set.of(allowedPermissions));
        PermissionChecker checker = request -> allowed.contains(request.permission())
                ? PermissionChecker.Decision.allow("untrusted-admin-looking-policy")
                : PermissionChecker.Decision.deny("test-deny");
        return new AccessControl(checker);
    }
}
