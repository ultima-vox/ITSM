package ru.ultimavox.itsm.servicedesk.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.servicedesk.ServiceDeskReportQuery;

@Service
final class JdbcServiceDeskReportQuery implements ServiceDeskReportQuery {
  private final JdbcTemplate jdbc;

  JdbcServiceDeskReportQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Snapshot snapshot() {
    String org = OrganizationContext.current();
    long open = count("""
        SELECT count(*) FROM work_item
        WHERE org_id = ? AND state NOT IN ('CLOSED', 'CANCELLED')
        """, org);
    long resolved = count("""
        SELECT count(*) FROM work_item
        WHERE org_id = ? AND state IN ('RESOLVED', 'CLOSED')
        """, org);
    long unassigned = count("""
        SELECT count(*) FROM work_item
        WHERE org_id = ?
          AND state NOT IN ('CLOSED', 'CANCELLED')
          AND (assignee_id IS NULL OR assignee_id = '')
        """, org);
    Double mttr = jdbc.query("""
        SELECT AVG(EXTRACT(EPOCH FROM (COALESCE(closed_at, updated_at) - created_at)) / 3600.0)
        FROM work_item WHERE org_id = ? AND state IN ('RESOLVED', 'CLOSED')
        """, rs -> rs.next() && rs.getObject(1) != null ? rs.getDouble(1) : null, org);

    Map<String, Long> aging = new LinkedHashMap<>();
    aging.put("0_1d", countAging(org, 0, 1));
    aging.put("1_3d", countAging(org, 1, 3));
    aging.put("3_7d", countAging(org, 3, 7));
    aging.put("7d_plus", countAging(org, 7, null));

    return new Snapshot(
        open, resolved, unassigned, mttr == null ? null : Math.round(mttr * 10.0) / 10.0,
        groupCount("priority", """
            SELECT priority, count(*) AS c FROM work_item
            WHERE org_id = ? AND state NOT IN ('CLOSED', 'CANCELLED') GROUP BY priority
            """, org),
        groupCount("state", "SELECT state, count(*) AS c FROM work_item WHERE org_id = ? GROUP BY state", org),
        groupCount("type", """
            SELECT type, count(*) AS c FROM work_item
            WHERE org_id = ? AND state NOT IN ('CLOSED', 'CANCELLED') GROUP BY type
            """, org),
        aging);
  }

  private long count(String sql, Object... args) {
    Long value = jdbc.queryForObject(sql, Long.class, args);
    return value == null ? 0L : value;
  }

  private long countAging(String org, int minDays, Integer maxDays) {
    if (minDays == 0) {
      return count("""
          SELECT count(*) FROM work_item
          WHERE org_id = ?
            AND state NOT IN ('CLOSED', 'CANCELLED')
            AND created_at > now() - make_interval(days => %d)
          """.formatted(maxDays), org);
    }
    if (maxDays == null) {
      return count("""
          SELECT count(*) FROM work_item
          WHERE org_id = ?
            AND state NOT IN ('CLOSED', 'CANCELLED')
            AND created_at <= now() - make_interval(days => %d)
          """.formatted(minDays), org);
    }
    return count("""
        SELECT count(*) FROM work_item
        WHERE org_id = ?
          AND state NOT IN ('CLOSED', 'CANCELLED')
          AND created_at <= now() - make_interval(days => %d)
          AND created_at > now() - make_interval(days => %d)
        """.formatted(minDays, maxDays), org);
  }

  private Map<String, Long> groupCount(String key, String sql, Object... args) {
    List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
    Map<String, Long> result = new LinkedHashMap<>();
    for (Map<String, Object> row : rows) {
      if (row.get(key) != null && row.get("c") instanceof Number count) {
        result.put(String.valueOf(row.get(key)), count.longValue());
      }
    }
    return result;
  }
}
