package ru.ultimavox.itsm.platform.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

/**
 * OpenSearch HTTP adapter. Active when {@code itsm.opensearch.url} / {@code OPENSEARCH_URL} is set.
 * Index + multi_match search; scope filtering reapplied client-side after hits return.
 */
@Service
@Conditional(OpenSearchEnabledCondition.class)
public class OpenSearchSearchIndexService implements SearchIndexService {

  private static final Logger log = LoggerFactory.getLogger(OpenSearchSearchIndexService.class);

  private final ItsmOpenSearchProperties props;
  private final ObjectMapper json;
  private final OpenSearchHttpClient http;

  @Autowired
  public OpenSearchSearchIndexService(ItsmOpenSearchProperties props, ObjectMapper json) {
    this(props, json, OpenSearchHttpClient.jdk(props.getConnectTimeout()));
  }

  /** Package-private for unit tests. */
  OpenSearchSearchIndexService(
      ItsmOpenSearchProperties props,
      ObjectMapper json,
      OpenSearchHttpClient http
  ) {
    this.props = props;
    this.json = json;
    this.http = http;
  }

  @Override
  public void index(SearchDocument document) {
    try {
      ObjectNode body = json.createObjectNode();
      body.put("id", document.id());
      body.put("objectType", document.objectType());
      body.put("title", document.title() == null ? "" : document.title());
      body.put("body", document.body() == null ? "" : document.body());
      body.put("updatedAt", (document.updatedAt() == null ? Instant.now() : document.updatedAt()).toString());
      ArrayNode scopes = body.putArray("scopes");
      for (String scope : document.scopes()) {
        scopes.add(scope);
      }
      body.set("facets", json.valueToTree(document.facets()));

      String path = indexName() + "/_doc/" + encode(document.id());
      HttpRequest request = OpenSearchHttpClient.request(uri(path), props.getReadTimeout())
          .header("Content-Type", "application/json")
          .PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
          .build();
      HttpResponse<String> response = http.send(request);
      if (response.statusCode() >= 300) {
        throw new IllegalStateException(
            "OpenSearch index failed status=" + response.statusCode() + " body=" + truncate(response.body()));
      }
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Cannot serialize OpenSearch document", ex);
    } catch (IOException | InterruptedException ex) {
      if (ex instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new IllegalStateException("OpenSearch index request failed", ex);
    }
  }

  @Override
  public void delete(String id) {
    try {
      String path = indexName() + "/_doc/" + encode(id);
      HttpRequest request = OpenSearchHttpClient.request(uri(path), props.getReadTimeout())
          .DELETE()
          .build();
      HttpResponse<String> response = http.send(request);
      // 404 is idempotent delete success
      if (response.statusCode() >= 300 && response.statusCode() != 404) {
        throw new IllegalStateException(
            "OpenSearch delete failed status=" + response.statusCode() + " body=" + truncate(response.body()));
      }
    } catch (IOException | InterruptedException ex) {
      if (ex instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new IllegalStateException("OpenSearch delete request failed", ex);
    }
  }

  @Override
  public List<SearchDocument> search(String query, Set<String> allowedScopes, int limit) {
    int safeLimit = Math.min(Math.max(limit, 1), 200);
    try {
      ObjectNode body = json.createObjectNode();
      body.put("size", safeLimit);
      ObjectNode queryNode = body.putObject("query");
      String q = query == null ? "" : query.trim();
      if (q.isEmpty()) {
        queryNode.putObject("match_all");
      } else {
        ObjectNode multi = queryNode.putObject("multi_match");
        multi.put("query", q);
        multi.putArray("fields").add("title^2").add("body");
      }

      String path = indexName() + "/_search";
      HttpRequest request = OpenSearchHttpClient.request(uri(path), props.getReadTimeout())
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
          .build();
      HttpResponse<String> response = http.send(request);
      if (response.statusCode() >= 300) {
        log.warn("OpenSearch search failed status={} body={}", response.statusCode(), truncate(response.body()));
        return List.of();
      }
      return filterScopes(parseHits(response.body()), allowedScopes);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Cannot build OpenSearch query", ex);
    } catch (IOException | InterruptedException ex) {
      if (ex instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      log.warn("OpenSearch search request failed: {}", ex.toString());
      return List.of();
    }
  }

  private List<SearchDocument> parseHits(String responseBody) throws JsonProcessingException {
    JsonNode root = json.readTree(responseBody);
    JsonNode hits = root.path("hits").path("hits");
    if (!hits.isArray()) {
      return List.of();
    }
    List<SearchDocument> docs = new ArrayList<>();
    for (JsonNode hit : hits) {
      JsonNode source = hit.path("_source");
      if (source.isMissingNode() || source.isNull()) {
        continue;
      }
      docs.add(new SearchDocument(
          text(source, "id", hit.path("_id").asText("")),
          text(source, "objectType", ""),
          text(source, "title", ""),
          text(source, "body", ""),
          readScopes(source.get("scopes")),
          readInstant(source.get("updatedAt")),
          readFacets(source.get("facets"))
      ));
    }
    return docs;
  }

  private static List<SearchDocument> filterScopes(List<SearchDocument> rows, Set<String> allowedScopes) {
    if (allowedScopes == null || allowedScopes.isEmpty()) {
      return rows;
    }
    List<SearchDocument> filtered = new ArrayList<>();
    for (SearchDocument doc : rows) {
      if (doc.scopes().isEmpty() || doc.scopes().stream().anyMatch(allowedScopes::contains)) {
        filtered.add(doc);
      }
    }
    return filtered;
  }

  private Set<String> readScopes(JsonNode node) {
    if (node == null || !node.isArray()) {
      return Set.of();
    }
    Set<String> scopes = new HashSet<>();
    for (JsonNode item : node) {
      if (item.isTextual()) {
        scopes.add(item.asText());
      }
    }
    return scopes;
  }

  private Map<String, Object> readFacets(JsonNode node) {
    if (node == null || node.isNull() || !node.isObject()) {
      return Map.of();
    }
    Map<String, Object> map = new HashMap<>();
    Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> entry = fields.next();
      JsonNode value = entry.getValue();
      if (value.isTextual()) {
        map.put(entry.getKey(), value.asText());
      } else if (value.isNumber()) {
        map.put(entry.getKey(), value.numberValue());
      } else if (value.isBoolean()) {
        map.put(entry.getKey(), value.booleanValue());
      } else if (!value.isNull()) {
        map.put(entry.getKey(), value.toString());
      }
    }
    return map;
  }

  private static Instant readInstant(JsonNode node) {
    if (node == null || !node.isTextual()) {
      return Instant.now();
    }
    try {
      return Instant.parse(node.asText());
    } catch (Exception ex) {
      return Instant.now();
    }
  }

  private static String text(JsonNode source, String field, String defaultValue) {
    JsonNode node = source.get(field);
    return node == null || node.isNull() ? defaultValue : node.asText(defaultValue);
  }

  private URI uri(String path) {
    String base = props.getUrl().endsWith("/")
        ? props.getUrl().substring(0, props.getUrl().length() - 1)
        : props.getUrl();
    return URI.create(base + "/" + path);
  }

  private String indexName() {
    String index = props.getIndex();
    return index == null || index.isBlank() ? "itsm" : index;
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private static String truncate(String body) {
    if (body == null) {
      return "";
    }
    return body.length() <= 400 ? body : body.substring(0, 400) + "…";
  }
}
