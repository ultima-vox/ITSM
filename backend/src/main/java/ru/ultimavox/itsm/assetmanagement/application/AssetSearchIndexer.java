package ru.ultimavox.itsm.assetmanagement.application;

import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.ultimavox.itsm.platform.search.SearchDocument;
import ru.ultimavox.itsm.platform.search.SearchIndexService;
import ru.ultimavox.itsm.assetmanagement.domain.Asset;

@Component
public class AssetSearchIndexer {

  private static final Logger log = LoggerFactory.getLogger(AssetSearchIndexer.class);
  private final SearchIndexService searchIndex;

  public AssetSearchIndexer(SearchIndexService searchIndex) {
    this.searchIndex = searchIndex;
  }

  public void index(Asset asset) {
    try {
      searchIndex.index(toDocument(asset));
    } catch (Exception ex) {
      log.warn("Search index failed for asset {}: {}", asset.id(), ex.toString());
    }
  }

  public void delete(String id) {
    try {
      searchIndex.delete(id);
    } catch (Exception ex) {
      log.warn("Search delete failed for asset {}: {}", id, ex.toString());
    }
  }

  static SearchDocument toDocument(Asset a) {
    String displayName = a.name() != null ? a.name() : a.assetTag() + " · " + a.kind().name().replace('_', ' ');
    String body = a.ownerSubject() == null ? "" : a.ownerSubject();
    if (a.location() != null) { if (!body.isEmpty()) body += " "; body += a.location(); }
    return new SearchDocument(
        a.id().toString(), "asset", displayName, body,
        Set.of("asset"), null,
        Map.of("assetTag", a.assetTag(), "kind", a.kind().name(), "status", a.status().name())
    );
  }
}
