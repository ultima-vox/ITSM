package ru.ultimavox.itsm.platform.search;

import java.util.List;
import java.util.Set;

/**
 * Search projection port. Implementations may be JDBC (dev/stub), OpenSearch, or no-op.
 * Query paths must re-authorize using scopes carried on the document.
 */
public interface SearchIndexService {

    void index(SearchDocument document);

    void delete(String id);

    List<SearchDocument> search(String query, Set<String> allowedScopes, int limit);
}
