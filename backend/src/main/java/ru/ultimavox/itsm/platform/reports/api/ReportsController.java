package ru.ultimavox.itsm.platform.reports.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ultimavox.itsm.platform.authorization.AccessControl;

/**
 * Aggregated operational reports from PostgreSQL (not mock synthesis).
 */
@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Platform — Reports")
class ReportsController {

  private final JdbcTemplate jdbc;
  private final AccessControl access;

  ReportsController(JdbcTemplate jdbc, AccessControl access) {
    this.jdbc = jdbc;
    this.access = access;
  }

  @GetMapping("/workload")
  @Operation(summary = "Workload and SLA snapshot from live work items")
  WorkloadReport workload(Authentication authentication) {
    access.require(authentication.getName(), "work-item.read", "work-item", null);

    long open = count(
        "SELECT count(*) FROM work_item WHERE state NOT IN ('CLOSED', 'CANCELLED')"
    );
    long resolved = count(
        "SELECT count(*) FROM work_item WHERE state IN ('RESOLVED', 'CLOSED')"
    );
    long unassigned = count(
        """
        SELECT count(*) FROM work_item
        WHERE state NOT IN ('CLOSED', 'CANCELLED')
          AND (assignee_id IS NULL OR assignee_id = '')
        """
    );

    long breached = 0;
    long atRisk = 0;
    try {
      breached = count(
          """
          SELECT count(DISTINCT sc.aggregate_id)
          FROM sla_clock sc
          JOIN work_item wi ON wi.id = sc.aggregate_id
          WHERE sc.state = 'BREACHED'
            AND wi.state NOT IN ('CLOSED', 'CANCELLED')
          """
      );
      atRisk = count(
          """
          SELECT count(DISTINCT sc.aggregate_id)
          FROM sla_clock sc
          JOIN work_item wi ON wi.id = sc.aggregate_id
          WHERE sc.state = 'RUNNING'
            AND sc.warning_at IS NOT NULL
            AND sc.warning_at <= now()
            AND sc.due_at > now()
            AND wi.state NOT IN ('CLOSED', 'CANCELLED')
          """
      );
    } catch (Exception ignored) {
      // SLA tables optional in sparse envs
    }

    Map<String, Long> byPriority = groupCount(
        """
        SELECT priority, count(*) AS c FROM work_item
        WHERE state NOT IN ('CLOSED', 'CANCELLED')
        GROUP BY priority
        """
    );
    Map<String, Long> byState = groupCount(
        """
        SELECT state, count(*) AS c FROM work_item
        GROUP BY state
        """
    );
    Map<String, Long> byType = groupCount(
        """
        SELECT type, count(*) AS c FROM work_item
        WHERE state NOT IN ('CLOSED', 'CANCELLED')
        GROUP BY type
        """
    );

    Map<String, Long> aging = new LinkedHashMap<>();
    aging.put("0_1d", countAging(0, 1));
    aging.put("1_3d", countAging(1, 3));
    aging.put("3_7d", countAging(3, 7));
    aging.put("7d_plus", countAging(7, null));

    Double mttrHours = jdbc.query(
        """
        SELECT AVG(EXTRACT(EPOCH FROM (COALESCE(closed_at, updated_at) - created_at)) / 3600.0)
        FROM work_item
        WHERE state IN ('RESOLVED', 'CLOSED')
        """,
        rs -> rs.next() ? rs.getObject(1) != null ? rs.getDouble(1) : null : null
    );

    return new WorkloadReport(
        open,
        resolved,
        unassigned,
        breached,
        atRisk,
        mttrHours == null ? null : Math.round(mttrHours * 10.0) / 10.0,
        byPriority,
        byState,
        byType,
        aging,
        "postgresql"
    );
  }

  private long count(String sql) {
    Long n = jdbc.queryForObject(sql, Long.class);
    return n == null ? 0L : n;
  }

  /**
   * Aging bucket by open age in days: [minDays, maxDays) where maxDays null = unbounded.
   * minDays=0,maxDays=1 → younger than 1 day.
   */
  private long countAging(int minDaysInclusive, Integer maxDaysExclusive) {
    if (minDaysInclusive == 0 && maxDaysExclusive != null) {
      return count(
          """
          SELECT count(*) FROM work_item
          WHERE state NOT IN ('CLOSED', 'CANCELLED')
            AND created_at > now() - make_interval(days => %d)
          """.formatted(maxDaysExclusive)
      );
    }
    if (maxDaysExclusive == null) {
      return count(
          """
          SELECT count(*) FROM work_item
          WHERE state NOT IN ('CLOSED', 'CANCELLED')
            AND created_at <= now() - make_interval(days => %d)
          """.formatted(minDaysInclusive)
      );
    }
    return count(
        """
        SELECT count(*) FROM work_item
        WHERE state NOT IN ('CLOSED', 'CANCELLED')
          AND created_at <= now() - make_interval(days => %d)
          AND created_at > now() - make_interval(days => %d)
        """.formatted(minDaysInclusive, maxDaysExclusive)
    );
  }

  private Map<String, Long> groupCount(String sql) {
    List<Map<String, Object>> rows = jdbc.queryForList(sql);
    Map<String, Long> out = new LinkedHashMap<>();
    for (Map<String, Object> row : rows) {
      Object k = row.get("priority");
      if (k == null) {
        k = row.get("state");
      }
      if (k == null) {
        k = row.get("type");
      }
      Object c = row.get("c");
      if (k != null && c instanceof Number n) {
        out.put(String.valueOf(k), n.longValue());
      }
    }
    return out;
  }

  record WorkloadReport(
      long open,
      long resolved,
      long unassigned,
      long breached,
      long atRisk,
      Double mttrHours,
      Map<String, Long> byPriority,
      Map<String, Long> byState,
      Map<String, Long> byType,
      Map<String, Long> agingBuckets,
      String source
  ) {}
}
