package ru.ultimavox.itsm.releasemanagement;

/** Public release metrics for operational reporting without exposing the release tables. */
public interface ReleaseReportQuery {
  Snapshot snapshot();

  record Snapshot(long inFlight, long deployed, long rolledBack, Double successRate) {}
}
