package ru.ultimavox.itsm.platform.automation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

/** Query side of the automation action log (read-only, org-scoped, newest first). */
@Service
public class AutomationActionLogQuery {

  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public AutomationActionLogQuery(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public List<AutomationActionLogEntry> list(String ruleKeyFilter, String statusFilter, int limit, int offset) {
    int cap = Math.min(Math.max(limit, 1), 200);
    int off = Math.max(offset, 0);
    boolean byRule = ruleKeyFilter != null && !ruleKeyFilter.isBlank();
    boolean byStatus = statusFilter != null && !statusFilter.isBlank() && !"all".equalsIgnoreCase(statusFilter);

    StringBuilder sql = new StringBuilder("""
        SELECT id, rule_key, event_id, action_type, status, details::text, created_at
        FROM automation_action_log
        WHERE org_id = ?
        """);
    List<Object> args = new ArrayList<>();
    args.add(OrganizationContext.current());
    if (byRule) {
      sql.append("AND rule_key = ? ");
      args.add(ruleKeyFilter);
    }
    if (byStatus) {
      sql.append("AND status = ? ");
      args.add(statusFilter);
    }
    sql.append("ORDER BY created_at DESC LIMIT ? OFFSET ?");
    args.add(cap);
    args.add(off);

    return jdbc.query(sql.toString(), (rs, i) -> mapRow(rs), args.toArray());
  }

  private AutomationActionLogEntry mapRow(ResultSet rs) throws SQLException {
    Timestamp ts = rs.getTimestamp("created_at");
    return new AutomationActionLogEntry(
        rs.getObject("id", UUID.class),
        rs.getString("rule_key"),
        rs.getObject("event_id", UUID.class),
        rs.getString("action_type"),
        rs.getString("status"),
        readMap(rs.getString("details")),
        ts == null ? Instant.EPOCH : ts.toInstant()
    );
  }

  private Map<String, Object> readMap(String raw) {
    if (raw == null || raw.isBlank()) {
      return Map.of();
    }
    try {
      Map<String, Object> m = json.readValue(raw, MAP);
      return m == null ? Map.of() : m;
    } catch (JsonProcessingException ex) {
      return Map.of();
    }
  }
}
