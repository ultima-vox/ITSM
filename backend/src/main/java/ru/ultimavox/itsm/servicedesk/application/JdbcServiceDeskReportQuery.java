package ru.ultimavox.itsm.servicedesk.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.servicedesk.ServiceDeskReportQuery;

@Service
final class JdbcServiceDeskReportQuery implements ServiceDeskReportQuery {
  private final JdbcTemplate jdbc;

  JdbcServiceDeskReportQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Snapshot snapshot() {
    long open = count("SELECT count(*) FROM work_item WHERE state NOT IN ('CLOSED', 'CANCELLED')");
    long resolved = count("SELECT count(*) FROM work_item WHERE state IN ('RESOLVED', 'CLOSED')");
    long unassigned = count("""
        SELECT count(*) FROM work_item
        WHERE state NOT IN ('CLOSED', 'CANCELLED')
          AND (assignee_id IS NULL OR assignee_id = '')
        """);
    Double mttr = jdbc.query("""
        SELECT AVG(EXTRACT(EPOCH FROM (COALESCE(closed_at, updated_at) - created_at)) / 3600.0)
        FROM work_item WHERE state IN ('RESOLVED', 'CLOSED')
        """, rs -> rs.next() && rs.getObject(1) != null ? rs.getDouble(1) : null);

    Map<String, Long> aging = new LinkedHashMap<>();
    aging.put("0_1d", countAging(0, 1));
    aging.put("1_3d", countAging(1, 3));
    aging.put("3_7d", countAging(3, 7));
    aging.put("7d_plus", countAging(7, null));

    return new Snapshot(
        open, resolved, unassigned, mttr == null ? null : Math.round(mttr * 10.0) / 10.0,
        groupCount("priority", """
            SELECT priority, count(*) AS c FROM work_item
            WHERE state NOT IN ('CLOSED', 'CANCELLED') GROUP BY priority
            """),
        groupCount("state", "SELECT state, count(*) AS c FROM work_item GROUP BY state"),
        groupCount("type", """
            SELECT type, count(*) AS c FROM work_item
            WHERE state NOT IN ('CLOSED', 'CANCELLED') GROUP BY type
            """),
        aging);
  }

  private long count(String sql) {
    Long value = jdbc.queryForObject(sql, Long.class);
    return value == null ? 0L : value;
  }

  private long countAging(int minDays, Integer maxDays) {
    if (minDays == 0) {
      return count("""
          SELECT count(*) FROM work_item
          WHERE state NOT IN ('CLOSED', 'CANCELLED')
            AND created_at > now() - make_interval(days => %d)
          """.formatted(maxDays));
    }
    if (maxDays == null) {
      return count("""
          SELECT count(*) FROM work_item
          WHERE state NOT IN ('CLOSED', 'CANCELLED')
            AND created_at <= now() - make_interval(days => %d)
          """.formatted(minDays));
    }
    return count("""
        SELECT count(*) FROM work_item
        WHERE state NOT IN ('CLOSED', 'CANCELLED')
          AND created_at <= now() - make_interval(days => %d)
          AND created_at > now() - make_interval(days => %d)
        """.formatted(minDays, maxDays));
  }

  private Map<String, Long> groupCount(String key, String sql) {
    List<Map<String, Object>> rows = jdbc.queryForList(sql);
    Map<String, Long> result = new LinkedHashMap<>();
    for (Map<String, Object> row : rows) {
      if (row.get(key) != null && row.get("c") instanceof Number count) {
        result.put(String.valueOf(row.get(key)), count.longValue());
      }
    }
    return result;
  }
}
