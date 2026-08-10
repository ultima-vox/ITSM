package ru.ultimavox.itsm.platform.sla;

public interface SlaReportQuery {
  Snapshot snapshot();

  record Snapshot(long breached, long atRisk) {}
}
