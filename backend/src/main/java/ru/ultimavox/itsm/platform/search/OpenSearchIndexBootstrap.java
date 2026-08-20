package ru.ultimavox.itsm.platform.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Ensures the OpenSearch index exists with a usable mapping when the compose stack is live.
 * Idempotent: 200/400 "resource_already_exists" are treated as success.
 */
@Component
@Order(50)
@Conditional(OpenSearchEnabledCondition.class)
class OpenSearchIndexBootstrap implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(OpenSearchIndexBootstrap.class);

  private final ItsmOpenSearchProperties props;
  private final ObjectMapper json;
  private final OpenSearchHttpClient http;

  @Autowired
  OpenSearchIndexBootstrap(ItsmOpenSearchProperties props, ObjectMapper json) {
    this(props, json,
        OpenSearchHttpClient.jdk(
            props.getConnectTimeout(), props.basicAuthorizationHeader(), props.getCaCertificate()));
  }

  /** Package-private for unit tests. */
  OpenSearchIndexBootstrap(
      ItsmOpenSearchProperties props,
      ObjectMapper json,
      OpenSearchHttpClient http
  ) {
    this.props = props;
    this.json = json;
    this.http = http;
  }

  @Override
  public void run(ApplicationArguments args) {
    String index = indexName();
    try {
      if (indexExists(index)) {
        log.info("OpenSearch index '{}' already present at {}", index, props.getUrl());
        return;
      }
      createIndex(index);
      log.info("OpenSearch index '{}' created at {}", index, props.getUrl());
    } catch (Exception ex) {
      // Degrade: app stays up; search returns empty until OpenSearch recovers.
      log.warn("OpenSearch index bootstrap failed (search will degrade): {}", ex.toString());
    }
  }

  private boolean indexExists(String index) throws IOException, InterruptedException {
    HttpRequest request = OpenSearchHttpClient.request(uri(index), props.getReadTimeout())
        .method("HEAD", HttpRequest.BodyPublishers.noBody())
        .build();
    HttpResponse<String> response = http.send(request);
    return response.statusCode() == 200;
  }

  private void createIndex(String index) throws IOException, InterruptedException {
    ObjectNode root = json.createObjectNode();
    ObjectNode settings = root.putObject("settings");
    settings.put("number_of_shards", 1);
    settings.put("number_of_replicas", 0);

    ObjectNode mappings = root.putObject("mappings");
    ObjectNode properties = mappings.putObject("properties");
    properties.putObject("id").put("type", "keyword");
    properties.putObject("objectType").put("type", "keyword");
    properties.putObject("title").put("type", "text").put("analyzer", "standard");
    properties.putObject("body").put("type", "text").put("analyzer", "standard");
    properties.putObject("scopes").put("type", "keyword");
    properties.putObject("updatedAt").put("type", "date");
    properties.putObject("facets").put("type", "object").put("enabled", true);

    HttpRequest request = OpenSearchHttpClient.request(uri(index), props.getReadTimeout())
        .header("Content-Type", "application/json")
        .PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(root)))
        .build();
    HttpResponse<String> response = http.send(request);
    int code = response.statusCode();
    if (code >= 300 && !alreadyExists(response.body())) {
      throw new IllegalStateException(
          "Create index failed status=" + code + " body=" + truncate(response.body()));
    }
  }

  private static boolean alreadyExists(String body) {
    return body != null && body.contains("resource_already_exists_exception");
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

  private static String truncate(String body) {
    if (body == null) {
      return "";
    }
    return body.length() <= 300 ? body : body.substring(0, 300) + "…";
  }
}
