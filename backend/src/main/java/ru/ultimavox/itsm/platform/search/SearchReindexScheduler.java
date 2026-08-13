package ru.ultimavox.itsm.platform.search;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

/**
 * Periodically reconciles the search projection from the authoritative source tables
 * (PostgreSQL) through {@link SearchReindexSource} contributors. This makes indexing
 * recoverable: after OpenSearch downtime, index drift, or a schema change, the projection is
 * rebuilt without replaying every mutation.
 *
 * <p>Reconciliation is best-effort and isolated per source/org; a failing source is logged and
 * does not block the others. Normal mutations continue to index synchronously, so the
 * scheduler is a safety net rather than the primary indexing path.
 */
@Component
class SearchReindexScheduler {

  static final int PAGE_SIZE = 200;

  private static final Logger log = LoggerFactory.getLogger(SearchReindexScheduler.class);

  private final List<SearchReindexSource> sources;
  private final SearchIndexService index;

  SearchReindexScheduler(List<SearchReindexSource> sources, SearchIndexService index) {
    this.sources = sources;
    this.index = index;
  }

  @Scheduled(
      fixedDelayString = "${itsm.search.reindex-interval:PT10M}",
      initialDelayString = "${itsm.search.reindex-initial-delay:PT2M}")
  public void reconcile() {
    for (SearchReindexSource source : sources) {
      reconcileSource(source);
    }
  }

  private void reconcileSource(SearchReindexSource source) {
    try {
      List<String> organizationIds = source.organizationIds();
      for (String organizationId : organizationIds) {
        OrganizationContext.runAs(organizationId, () -> reconcileOrg(source, organizationId));
      }
    } catch (RuntimeException failure) {
      log.warn("Search reindex source={} failed: {}", source.sourceName(), failure.toString());
    }
  }

  private Void reconcileOrg(SearchReindexSource source, String organizationId) {
    int offset = 0;
    int total = 0;
    List<SearchDocument> page;
    do {
      page = source.snapshotPage(organizationId, offset, PAGE_SIZE);
      for (SearchDocument document : page) {
        try {
          index.index(document);
        } catch (RuntimeException failure) {
          log.warn("Search reindex source={} org={} doc={} failed: {}",
              source.sourceName(), organizationId, document.id(), failure.toString());
        }
      }
      total += page.size();
      offset += page.size();
    } while (page.size() == PAGE_SIZE);
    if (total > 0) {
      log.info("Search reindex source={} org={} indexed={}",
          source.sourceName(), organizationId, total);
    }
    return null;
  }
}
