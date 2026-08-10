package ru.ultimavox.itsm.servicedesk;

import java.util.Map;

/** Public read contract for operational reporting without exposing Service Desk tables. */
public interface ServiceDeskReportQuery {
  Snapshot snapshot();

  record Snapshot(
      long open,
      long resolved,
      long unassigned,
      Double mttrHours,
      Map<String, Long> byPriority,
      Map<String, Long> byState,
      Map<String, Long> byType,
      Map<String, Long> agingBuckets
  ) {}
}
