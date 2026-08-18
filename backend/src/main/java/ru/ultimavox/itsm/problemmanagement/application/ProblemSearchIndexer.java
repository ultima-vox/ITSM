package ru.ultimavox.itsm.problemmanagement.application;

import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.ultimavox.itsm.platform.search.SearchDocument;
import ru.ultimavox.itsm.platform.search.SearchIndexService;
import ru.ultimavox.itsm.problemmanagement.domain.Problem;

@Component
public class ProblemSearchIndexer {

  private static final Logger log = LoggerFactory.getLogger(ProblemSearchIndexer.class);
  private final SearchIndexService searchIndex;

  public ProblemSearchIndexer(SearchIndexService searchIndex) {
    this.searchIndex = searchIndex;
  }

  public void index(Problem problem) {
    try {
      searchIndex.index(toDocument(problem));
    } catch (Exception ex) {
      log.warn("Search index failed for problem {}: {}", problem.id(), ex.toString());
    }
  }

  public void delete(String id) {
    try {
      searchIndex.delete(id);
    } catch (Exception ex) {
      log.warn("Search delete failed for problem {}: {}", id, ex.toString());
    }
  }

  static SearchDocument toDocument(Problem p) {
    String title = p.number() + " · " + p.title();
    StringBuilder body = new StringBuilder();
    if (p.rootCause() != null) body.append(p.rootCause());
    if (p.workaround() != null) { if (!body.isEmpty()) body.append(" "); body.append(p.workaround()); }
    if (p.resolution() != null) { if (!body.isEmpty()) body.append(" "); body.append(p.resolution()); }
    return new SearchDocument(
        p.id().toString(), "problem", title, body.toString(),
        Set.of("problem"), null,
        Map.of("number", p.number(), "status", p.status().name())
    );
  }
}
