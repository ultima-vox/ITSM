package ru.ultimavox.itsm.platform.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * No-op adapter for environments without search persistence.
 * Not a Spring bean by default — {@link JdbcSearchIndexService} is the active stub.
 */
public class NoOpSearchIndexService implements SearchIndexService {

    private static final Logger log = LoggerFactory.getLogger(NoOpSearchIndexService.class);

    @Override
    public void index(SearchDocument document) {
        log.debug("search no-op index id={} type={}", document.id(), document.objectType());
    }

    @Override
    public void delete(String id) {
        log.debug("search no-op delete id={}", id);
    }

    @Override
    public List<SearchDocument> search(String query, Set<String> allowedScopes, int limit) {
        return List.of();
    }
}
