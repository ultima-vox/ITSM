package ru.ultimavox.itsm.servicedesk.application;

import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.ultimavox.itsm.platform.search.SearchDocument;
import ru.ultimavox.itsm.platform.search.SearchIndexService;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;

/**
 * Projects work items into the platform search index (JDBC or OpenSearch).
 * Failures are logged and swallowed so core mutations never fail because search is down.
 */
@Component
public class WorkItemSearchIndexer {

  private static final Logger log = LoggerFactory.getLogger(WorkItemSearchIndexer.class);

  private final SearchIndexService searchIndex;

  public WorkItemSearchIndexer(SearchIndexService searchIndex) {
    this.searchIndex = searchIndex;
  }

  public void index(WorkItem item) {
    try {
      searchIndex.index(toDocument(item));
    } catch (Exception ex) {
      log.warn("Search index failed for work-item {}: {}", item.id(), ex.toString());
    }
  }

  public void delete(String id) {
    try {
      searchIndex.delete(id);
    } catch (Exception ex) {
      log.warn("Search delete failed for work-item {}: {}", id, ex.toString());
    }
  }

  static SearchDocument toDocument(WorkItem item) {
    String title = item.number() + " · " + item.title();
    return new SearchDocument(
        item.id().toString(),
        "work-item",
        title,
        item.description() == null ? "" : item.description(),
        Set.of("work-item", item.type().name().toLowerCase(java.util.Locale.ROOT)),
        item.updatedAt(),
        Map.of(
            "number", item.number(),
            "state", item.state().name(),
            "priority", item.priority().name(),
            "service", item.service()
        )
    );
  }
}
