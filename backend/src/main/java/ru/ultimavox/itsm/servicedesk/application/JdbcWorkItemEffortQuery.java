package ru.ultimavox.itsm.servicedesk.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.servicedesk.WorkItemEffortQuery;

@Service
final class JdbcWorkItemEffortQuery implements WorkItemEffortQuery {
  private final JdbcTemplate jdbc;

  JdbcWorkItemEffortQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Snapshot snapshot() {
    String org = OrganizationContext.current();
    return jdbc.queryForObject(
        """
            SELECT count(*) AS entries,
                   COALESCE(sum(minutes), 0) AS total_minutes,
                   COALESCE(sum(minutes) FILTER (WHERE billable), 0) AS billable_minutes,
                   count(DISTINCT work_item_id) AS items_with_effort
            FROM work_item_worklog WHERE org_id = ?
            """,
        (rs, row) -> new Snapshot(
            rs.getLong("entries"),
            rs.getLong("total_minutes"),
            rs.getLong("billable_minutes"),
            rs.getLong("items_with_effort")),
        org);
  }
}
