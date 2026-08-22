package ru.ultimavox.itsm.changemanagement;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Public change lookup for modules that group changes, without exposing the change tables. */
public interface ChangeCatalogQuery {
  /** Summaries for the requested ids, scoped to the caller's organization. Unknown ids are skipped. */
  List<ChangeSummary> summaries(Collection<UUID> ids);

  record ChangeSummary(
      UUID id,
      String number,
      String title,
      String type,
      String status,
      Instant plannedStart,
      Instant plannedEnd
  ) {}
}
