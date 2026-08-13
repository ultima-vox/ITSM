package ru.ultimavox.itsm.platform.search;

import java.util.List;

/**
 * Read-only projection source used by the search reconciliation job. Implementations live in
 * the module that owns the authoritative data and read directly from that module's tables, so
 * the search index (OpenSearch or JDBC) can be rebuilt on demand after downtime, drift, or a
 * schema upgrade. The scheduler re-runs each organization scope independently.
 */
public interface SearchReindexSource {

  /** Stable, human-readable name used in reconciliation logs and metrics (e.g. "work-item"). */
  String sourceName();

  /** Distinct organization identifiers present in this source's data. */
  List<String> organizationIds();

  /**
   * Page of documents for one organization, ordered by updated-at descending.
   *
   * @param offset zero-based row offset
   * @param limit maximum page size
   */
  List<SearchDocument> snapshotPage(String organizationId, int offset, int limit);
}
