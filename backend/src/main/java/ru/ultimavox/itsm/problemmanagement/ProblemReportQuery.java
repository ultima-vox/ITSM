package ru.ultimavox.itsm.problemmanagement;

/** Public problem metrics for operational reporting without exposing problem tables. */
public interface ProblemReportQuery {
  Snapshot snapshot();

  record Snapshot(long open, long knownErrors, long resolved) {}
}
