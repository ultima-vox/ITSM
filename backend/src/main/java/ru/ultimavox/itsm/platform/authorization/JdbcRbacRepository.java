package ru.ultimavox.itsm.platform.authorization;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
class JdbcRbacRepository implements RbacRepository {

    private final JdbcTemplate jdbc;

    JdbcRbacRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
}
