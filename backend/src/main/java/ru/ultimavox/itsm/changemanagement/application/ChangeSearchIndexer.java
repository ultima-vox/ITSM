package ru.ultimavox.itsm.changemanagement.application;

import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.ultimavox.itsm.platform.search.SearchDocument;
import ru.ultimavox.itsm.platform.search.SearchIndexService;
import ru.ultimavox.itsm.changemanagement.domain.Change;

@Component
public class ChangeSearchIndexer {

  private static final Logger log = LoggerFactory.getLogger(ChangeSearchIndexer.class);
  private final SearchIndexService searchIndex;

  public ChangeSearchIndexer(SearchIndexService searchIndex) {
    this.searchIndex = searchIndex;
  }

  public void index(Change change) {
    try {
      searchIndex.index(toDocument(change));
    } catch (Exception ex) {
      log.warn("Search index failed for change {}: {}", change.id(), ex.toString());
    }
  }

  public void delete(String id) {
    try {
      searchIndex.delete(id);
    } catch (Exception ex) {
      log.warn("Search delete failed for change {}: {}", id, ex.toString());
    }
  }

  static SearchDocument toDocument(Change c) {
    String title = c.number() + " · " + c.title();
    StringBuilder body = new StringBuilder();
    if (c.implementationPlan() != null) body.append(c.implementationPlan());
    if (c.rollbackPlan() != null) { if (!body.isEmpty()) body.append(" "); body.append(c.rollbackPlan()); }
    if (c.businessJustification() != null) { if (!body.isEmpty()) body.append(" "); body.append(c.businessJustification()); }
    return new SearchDocument(
        c.id().toString(), "change", title, body.toString(),
        Set.of("change"), null,
        Map.of("number", c.number(), "status", c.status().name(), "type", c.type().name())
    );
  }
}
