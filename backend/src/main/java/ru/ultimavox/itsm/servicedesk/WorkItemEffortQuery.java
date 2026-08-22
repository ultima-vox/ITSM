package ru.ultimavox.itsm.servicedesk;

/** Public effort metrics for operational reporting without exposing the worklog table. */
public interface WorkItemEffortQuery {
  Snapshot snapshot();

  record Snapshot(long entries, long totalMinutes, long billableMinutes, long itemsWithEffort) {}
}
