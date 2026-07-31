package ru.ultimavox.itsm.platform.authorization;

import java.util.Set;

/** Reads persisted RBAC grants (roles, permissions, principal assignments). */
public interface RbacRepository {

    /** All permission keys granted to the subject via principal_role → role_permission. */
    Set<String> permissionsForSubject(String subjectId);

    /** Role keys assigned to the subject. */
    Set<String> rolesForSubject(String subjectId);

    /** Whether subject has the given permission key. */
    boolean hasPermission(String subjectId, String permissionKey);
}
