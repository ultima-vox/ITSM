package ru.ultimavox.itsm.platform.identity;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

@Service
public class IdentityQueryService {
  static final int MAX_PAGE_SIZE = 200;

  private final JdbcTemplate jdbc;

  public IdentityQueryService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<IdentityAccountRecord> listAccounts(int page, int size) {
    int cap = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    int offset = Math.max(page, 0) * cap;
    List<IdentityAccountRecord> accounts = jdbc.query(
        """
            SELECT id, idp, external_id, subject_id, enabled, last_sync
            FROM identity_account
            ORDER BY last_sync DESC NULLS LAST, subject_id ASC, idp ASC
            LIMIT ? OFFSET ?
            """,
        (rs, i) -> new IdentityAccountRecord(
            rs.getObject("id", UUID.class),
            rs.getString("idp"),
            rs.getString("external_id"),
            rs.getString("subject_id"),
            rs.getBoolean("enabled"),
            toInstant(rs.getTimestamp("last_sync")),
            List.of()
        ),
        cap,
        offset
    );
    if (accounts.isEmpty()) {
      return List.of();
    }
    List<String> subjects = accounts.stream().map(IdentityAccountRecord::subjectId).distinct().toList();
    Map<String, List<String>> rolesBySubject = new HashMap<>();
    jdbc.query(
        """
            SELECT pr.subject_id, r.role_key
            FROM principal_role pr
            JOIN role r ON r.id = pr.role_id
            WHERE pr.org_id = ? AND pr.subject_id = ANY(?)
            ORDER BY r.role_key
            """,
        rs -> {
          rolesBySubject
              .computeIfAbsent(rs.getString("subject_id"), k -> new ArrayList<>())
              .add(rs.getString("role_key"));
        },
        OrganizationContext.current(),
        (Object) subjects.toArray(String[]::new)
    );
    return accounts.stream()
        .map(account -> new IdentityAccountRecord(
            account.id(),
            account.idp(),
            account.externalId(),
            account.subjectId(),
            account.enabled(),
            account.lastSync(),
            List.copyOf(rolesBySubject.getOrDefault(account.subjectId(), List.of()))
        ))
        .toList();
  }

  public List<GroupRoleMappingRecord> listGroupMappings(int page, int size) {
    int cap = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    int offset = Math.max(page, 0) * cap;
    return jdbc.query(
        """
            SELECT idp_group, role_name
            FROM group_role_mapping
            ORDER BY idp_group
            LIMIT ? OFFSET ?
            """,
        (rs, i) -> new GroupRoleMappingRecord(rs.getString("idp_group"), rs.getString("role_name")),
        cap,
        offset
    );
  }

  private static Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }

  public record IdentityAccountRecord(
      UUID id,
      String idp,
      String externalId,
      String subjectId,
      boolean enabled,
      Instant lastSync,
      List<String> roleKeys
  ) {}

  public record GroupRoleMappingRecord(String idpGroup, String roleName) {}
}
