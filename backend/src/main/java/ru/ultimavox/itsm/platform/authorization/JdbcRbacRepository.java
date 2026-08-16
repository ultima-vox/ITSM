package ru.ultimavox.itsm.platform.authorization;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRbacRepository implements RbacRepository {

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  JdbcRbacRepository(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public Set<String> permissionsForSubject(String subjectId) {
    List<String> rows = jdbc.query(
        """
            SELECT DISTINCT p.permission_key
            FROM principal_role pr
            JOIN role_permission rp ON rp.role_id = pr.role_id
            JOIN permission p ON p.id = rp.permission_id
            WHERE pr.subject_id = ?
            UNION
            SELECT g.permission
            FROM rbac_grant g
            WHERE g.subject_type = 'USER' AND g.subject_id = ?
            """,
        (rs, i) -> rs.getString(1),
        subjectId, subjectId
    );
    return new HashSet<>(rows);
  }

  @Override
  public Set<String> rolesForSubject(String subjectId) {
    List<String> rows = jdbc.query(
        """
            SELECT r.role_key
            FROM principal_role pr
            JOIN role r ON r.id = pr.role_id
            WHERE pr.subject_id = ?
            """,
        (rs, i) -> rs.getString(1),
        subjectId
    );
    return new HashSet<>(rows);
  }

  @Override
  public boolean hasPermission(String subjectId, String permissionKey) {
    Integer count = jdbc.queryForObject(
        """
            SELECT COUNT(*) FROM (
              SELECT 1
              FROM principal_role pr
              JOIN role_permission rp ON rp.role_id = pr.role_id
              JOIN permission p ON p.id = rp.permission_id
              WHERE pr.subject_id = ? AND p.permission_key = ?
              UNION ALL
              SELECT 1
              FROM rbac_grant g
              WHERE g.subject_type = 'USER' AND g.subject_id = ? AND g.permission = ?
              UNION ALL
              SELECT 1
              FROM principal_role pr
              JOIN role r ON r.id = pr.role_id
              JOIN role_permission rp ON rp.role_id = r.id
              JOIN permission p ON p.id = rp.permission_id
              WHERE pr.subject_id = ? AND p.permission_key = 'admin.full'
            ) grants
            """,
        Integer.class,
        subjectId, permissionKey,
        subjectId, permissionKey,
        subjectId
    );
    return count != null && count > 0;
  }

  @Override
  public List<RoleCatalogEntry> listRoles() {
    List<RoleRow> roles = jdbc.query(
        """
            SELECT id, role_key, labels::text, description
            FROM role
            ORDER BY role_key
            """,
        (rs, i) -> new RoleRow(
            rs.getObject("id", UUID.class),
            rs.getString("role_key"),
            rs.getString("labels"),
            rs.getString("description")
        )
    );
    Map<UUID, List<String>> permsByRole = new HashMap<>();
    jdbc.query(
        """
            SELECT rp.role_id, p.permission_key
            FROM role_permission rp
            JOIN permission p ON p.id = rp.permission_id
            ORDER BY p.permission_key
            """,
        (rs) -> {
          UUID roleId = rs.getObject("role_id", UUID.class);
          permsByRole.computeIfAbsent(roleId, k -> new ArrayList<>())
              .add(rs.getString("permission_key"));
        }
    );
    List<RoleCatalogEntry> out = new ArrayList<>();
    for (RoleRow row : roles) {
      out.add(new RoleCatalogEntry(
          row.id(),
          row.roleKey(),
          parseLabels(row.labelsJson()),
          row.description() == null ? "" : row.description(),
          List.copyOf(permsByRole.getOrDefault(row.id(), List.of()))
      ));
    }
    return out;
  }

  @Override
  public List<PermissionCatalogEntry> listPermissions() {
    return jdbc.query(
        """
            SELECT permission_key, description
            FROM permission
            ORDER BY permission_key
            """,
        (rs, i) -> new PermissionCatalogEntry(
            rs.getString("permission_key"),
            rs.getString("description") == null ? "" : rs.getString("description")
        )
    );
  }

  @Override
  public List<PrincipalAssignment> listPrincipalAssignments() {
    Map<String, List<String>> bySubject = new LinkedHashMap<>();
    jdbc.query(
        """
            SELECT pr.subject_id, r.role_key
            FROM principal_role pr
            JOIN role r ON r.id = pr.role_id
            ORDER BY pr.subject_id, r.role_key
            """,
        (rs) -> {
          String subject = rs.getString("subject_id");
          bySubject.computeIfAbsent(subject, k -> new ArrayList<>())
              .add(rs.getString("role_key"));
        }
    );
    List<PrincipalAssignment> out = new ArrayList<>();
    for (Map.Entry<String, List<String>> e : bySubject.entrySet()) {
      out.add(new PrincipalAssignment(e.getKey(), List.copyOf(e.getValue())));
    }
    return out;
  }

  private Map<String, String> parseLabels(String labelsJson) {
    if (labelsJson == null || labelsJson.isBlank()) {
      return Map.of();
    }
    try {
      return json.readValue(labelsJson, new TypeReference<Map<String, String>>() {});
    } catch (Exception ex) {
      return Map.of();
    }
  }

  private record RoleRow(UUID id, String roleKey, String labelsJson, String description) {}
}
