package ru.ultimavox.itsm.assetmanagement;

/** Public asset metrics for operational reporting without exposing asset tables. */
public interface AssetReportQuery {
  Snapshot snapshot();

  record Snapshot(long total, long inUse, long inStock) {}
}
