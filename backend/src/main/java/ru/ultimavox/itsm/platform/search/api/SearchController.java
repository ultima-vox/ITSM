package ru.ultimavox.itsm.platform.search.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.search.SearchDocument;
import ru.ultimavox.itsm.platform.search.SearchIndexService;

@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Search")
class SearchController {

  private final SearchIndexService searchIndex;
  private final AccessControl access;

  SearchController(SearchIndexService searchIndex, AccessControl access) {
    this.searchIndex = searchIndex;
    this.access = access;
  }

  @GetMapping
  @Operation(summary = "Full-text search across indexed projections (scope-filtered)")
  List<SearchHit> search(
      Authentication authentication,
      @RequestParam(name = "q", defaultValue = "") @jakarta.validation.constraints.Size(max = 2000) String q,
      @RequestParam(required = false) String scopes,
      @RequestParam(required = false, defaultValue = "50") int limit
  ) {
    access.require(authentication.getName(), "search.read", "search", null);
    Set<String> allowed = parseScopes(scopes);
    return searchIndex.search(q, allowed, limit).stream()
        .map(SearchHit::from)
        .toList();
  }

  private static Set<String> parseScopes(String scopes) {
    if (scopes == null || scopes.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(scopes.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
  }

  record SearchHit(
      String id,
      String objectType,
      String title,
      String body,
      Set<String> scopes,
      Instant updatedAt,
      Map<String, Object> facets
  ) {
    static SearchHit from(SearchDocument doc) {
      return new SearchHit(
          doc.id(),
          doc.objectType(),
          doc.title(),
          doc.body(),
          doc.scopes(),
          doc.updatedAt(),
          doc.facets()
      );
    }
  }
}
