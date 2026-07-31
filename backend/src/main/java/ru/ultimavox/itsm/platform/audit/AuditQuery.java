package ru.ultimavox.itsm.platform.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Query side of the platform audit trail. */
@Service
public class AuditQuery {

  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public AuditQuery(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public List<AuditEventRecord> list(String actionFilter, int limit, int offset) {
    int cap = Math.min(Math.max(limit, 1), 200);
    int off = Math.max(offset, 0);
    boolean filter = actionFilter != null && !actionFilter.isBlank() && !"all".equalsIgnoreCase(actionFilter);

    if (filter) {
      return jdbc.query(
          """
          SELECT id, occurred_at, actor_id, action, object_type, object_id,
                 before_state::text, after_state::text, correlation_id, metadata::text
          FROM audit_event
          WHERE action = ?
          ORDER BY occurred_at DESC
          LIMIT ? OFFSET ?
          """,
          (rs, i) -> mapRow(rs),
          actionFilter,
          cap,
          off
      );
    }
    return jdbc.query(
        """
        SELECT id, occurred_at, actor_id, action, object_type, object_id,
               before_state::text, after_state::text, correlation_id, metadata::text
        FROM audit_event
        ORDER BY occurred_at DESC
        LIMIT ? OFFSET ?
        """,
        (rs, i) -> mapRow(rs),
        cap,
        off
    );
  }

  public List<String> distinctActions(int limit) {
    int cap = Math.min(Math.max(limit, 1), 100);
    return jdbc.query(
        """
        SELECT DISTINCT action FROM audit_event
        ORDER BY action
        LIMIT ?
        """,
        (rs, i) -> rs.getString(1),
        cap
    );
  }

  private AuditEventRecord mapRow(ResultSet rs) throws SQLException {
    Timestamp ts = rs.getTimestamp("occurred_at");
    return new AuditEventRecord(
        rs.getObject("id", UUID.class),
        ts == null ? Instant.EPOCH : ts.toInstant(),
        rs.getString("actor_id"),
        rs.getString("action"),
        rs.getString("object_type"),
        rs.getString("object_id"),
        readMap(rs.getString("before_state")),
        readMap(rs.getString("after_state")),
        rs.getObject("correlation_id", UUID.class),
        readMap(rs.getString("metadata"))
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
