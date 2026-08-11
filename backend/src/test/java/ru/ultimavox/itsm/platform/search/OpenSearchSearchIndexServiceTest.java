package ru.ultimavox.itsm.platform.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class OpenSearchSearchIndexServiceTest {

  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

  @Test
  void index_posts_document_to_opensearch_path() {
    AtomicReference<HttpRequest> seen = new AtomicReference<>();
    OpenSearchHttpClient http = request -> {
      seen.set(request);
      return response(201, "{\"result\":\"created\"}");
    };
    ItsmOpenSearchProperties props = props("http://opensearch.local:9200", "itsm-docs");
    OpenSearchSearchIndexService service = new OpenSearchSearchIndexService(props, json, http);

    service.index(new SearchDocument(
        "work-item:1",
        "work-item",
        "Printer down",
        "Cannot print",
        Set.of("site:msk"),
        Instant.parse("2026-01-01T00:00:00Z"),
        Map.of("priority", "high")
    ));

    assertThat(seen.get()).isNotNull();
    assertThat(seen.get().method()).isEqualTo("PUT");
    assertThat(seen.get().uri().toString())
        .isEqualTo("http://opensearch.local:9200/itsm-docs/_doc/default%3Awork-item%3A1");
  }

  @Test
  void search_parses_hits_and_filters_scopes() {
    String body = """
        {
          "hits": {
            "hits": [
              {
                "_id": "a",
                "_source": {
                  "id": "a",
                  "objectType": "work-item",
                  "title": "Alpha",
                  "body": "one",
                  "scopes": ["site:msk"],
                  "updatedAt": "2026-01-01T00:00:00Z",
                  "facets": {}
                }
              },
              {
                "_id": "b",
                "_source": {
                  "id": "b",
                  "objectType": "work-item",
                  "title": "Beta",
                  "body": "two",
                  "scopes": ["site:spb"],
                  "updatedAt": "2026-01-02T00:00:00Z",
                  "facets": {}
                }
              }
            ]
          }
        }
        """;
    OpenSearchHttpClient http = request -> response(200, body);
    OpenSearchSearchIndexService service =
        new OpenSearchSearchIndexService(props("http://localhost:9200", "itsm"), json, http);

    List<SearchDocument> hits = service.search("printer", Set.of("site:msk"), 10);
    assertThat(hits).hasSize(1);
    assertThat(hits.getFirst().id()).isEqualTo("a");
  }

  @Test
  void search_returns_empty_on_http_error_without_throwing() {
    OpenSearchHttpClient http = request -> response(503, "unavailable");
    OpenSearchSearchIndexService service =
        new OpenSearchSearchIndexService(props("http://localhost:9200", "itsm"), json, http);

    assertThat(service.search("q", Set.of(), 10)).isEmpty();
  }

  @Test
  void enabled_condition_requires_non_blank_url() {
    assertThat(new ItsmOpenSearchProperties().isConfigured()).isFalse();
    ItsmOpenSearchProperties configured = new ItsmOpenSearchProperties();
    configured.setUrl("http://localhost:9200");
    assertThat(configured.isConfigured()).isTrue();
  }

  private static ItsmOpenSearchProperties props(String url, String index) {
    ItsmOpenSearchProperties props = new ItsmOpenSearchProperties();
    props.setUrl(url);
    props.setIndex(index);
    return props;
  }

  private static HttpResponse<String> response(int status, String body) {
    return new HttpResponse<>() {
      @Override
      public int statusCode() {
        return status;
      }

      @Override
      public HttpRequest request() {
        return null;
      }

      @Override
      public Optional<HttpResponse<String>> previousResponse() {
        return Optional.empty();
      }

      @Override
      public HttpHeaders headers() {
        return HttpHeaders.of(Map.of(), (a, b) -> true);
      }

      @Override
      public String body() {
        return body;
      }

      @Override
      public Optional<SSLSession> sslSession() {
        return Optional.empty();
      }

      @Override
      public URI uri() {
        return URI.create("http://localhost");
      }

      @Override
      public HttpClient.Version version() {
        return HttpClient.Version.HTTP_1_1;
      }
    };
  }
}
