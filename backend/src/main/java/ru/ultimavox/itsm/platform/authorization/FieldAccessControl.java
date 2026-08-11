package ru.ultimavox.itsm.platform.authorization;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Metadata-backed, fail-closed field authorization. */
@Component
public class FieldAccessControl {
  private final JdbcTemplate jdbc;
  private final PermissionChecker permissions;

  public FieldAccessControl(JdbcTemplate jdbc, PermissionChecker permissions) {
    this.jdbc = jdbc;
    this.permissions = permissions;
  }

  public void requireWrite(
      String subject, String objectType, String objectId, String field, String targetState) {
    require(subject, objectType, objectId, field, "WRITE", targetState);
  }

  public void requireRead(
      String subject, String objectType, String objectId, String field, String currentState) {
    require(subject, objectType, objectId, field, "READ", currentState);
  }

  private void require(String subject, String objectType, String objectId, String field,
                       String operation, String state) {
    if (subject == null || subject.isBlank()) {
      throw new AccessDeniedException("Field access requires authenticated subject");
    }
    List<Rule> rules = jdbc.query("""
        SELECT org_id,object_state,required_permission
        FROM field_access_policy
        WHERE org_id IN ('*',?) AND object_type=? AND field_key=? AND operation=? AND active
        ORDER BY CASE WHEN org_id=? THEN 0 ELSE 1 END
        """, (rs, row) -> new Rule(rs.getString(1), rs.getString(2), rs.getString(3)),
        OrganizationContext.current(), objectType, field, operation, OrganizationContext.current());
    if (rules.isEmpty()) return;

    String organization = OrganizationContext.current();
    List<Rule> local = rules.stream().filter(rule -> organization.equals(rule.organization())).toList();
    List<Rule> effective = local.isEmpty() ? rules : local;

    boolean allowed = effective.stream()
        .filter(rule -> rule.state() == null || rule.state().equals(state))
        .anyMatch(rule -> permissions.check(new PermissionChecker.Request(
            subject, rule.permission(), objectType, objectId, field)).allowed());
    if (!allowed) {
      throw new AccessDeniedException(
          "Permission denied for field " + objectType + "." + field + " in state " + state);
    }
  }

  private record Rule(String organization, String state, String permission) {}
}
