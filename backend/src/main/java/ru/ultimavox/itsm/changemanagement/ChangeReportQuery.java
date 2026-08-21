package ru.ultimavox.itsm.changemanagement;

/** Public change metrics for operational reporting without exposing change tables. */
public interface ChangeReportQuery {
  Snapshot snapshot();

  record Snapshot(long open, long closed, long rejected, Double successRate) {}
}
