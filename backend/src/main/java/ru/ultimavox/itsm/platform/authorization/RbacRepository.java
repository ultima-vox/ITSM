package ru.ultimavox.itsm.platform.authorization;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Reads persisted RBAC grants (roles, permissions, principal assignments). */
public interface RbacRepository {

    /** All permission keys granted to the subject via principal_role → role_permission. */
    Set<String> permissionsForSubject(String subjectId);

    /** Role keys assigned to the subject. */
    Set<String> rolesForSubject(String subjectId);

    /** Whether subject has the given permission key. */
    boolean hasPermission(String subjectId, String permissionKey);

    List<RoleCatalogEntry> listRoles();

    List<PermissionCatalogEntry> listPermissions();

    List<PrincipalAssignment> listPrincipalAssignments();

    record RoleCatalogEntry(
        UUID id,
        String roleKey,
        Map<String, String> labels,
        String description,
        List<String> permissions
    ) {}

    record PermissionCatalogEntry(String key, String description) {}

    record PrincipalAssignment(String subjectId, List<String> roleKeys) {}
}
