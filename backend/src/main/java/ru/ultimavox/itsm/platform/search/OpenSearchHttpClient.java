package ru.ultimavox.itsm.platform.search;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Thin HTTP facade over OpenSearch REST so unit tests can substitute a fake without a live cluster.
 */
public interface OpenSearchHttpClient {

  HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;

  static OpenSearchHttpClient jdk(Duration connectTimeout) {
    HttpClient client = HttpClient.newBuilder()
        .connectTimeout(connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout)
        .build();
    return request -> client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  static HttpRequest.Builder request(URI uri, Duration readTimeout) {
    Duration timeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
    return HttpRequest.newBuilder(uri).timeout(timeout);
  }
}
