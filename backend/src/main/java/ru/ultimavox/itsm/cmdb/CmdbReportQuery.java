package ru.ultimavox.itsm.cmdb;

/** Public CMDB metrics for operational reporting without exposing CMDB tables. */
public interface CmdbReportQuery {
  Snapshot snapshot();

  record Snapshot(long configurationItems, long orphans, long relationships) {}
}
