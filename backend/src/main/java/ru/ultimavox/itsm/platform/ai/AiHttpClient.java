package ru.ultimavox.itsm.platform.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Thin HTTP facade over the OpenAI-compatible chat completion endpoint so unit
 * tests can substitute a fake without a live model server.
 */
interface AiHttpClient {

  HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;

  static AiHttpClient jdk(Duration connectTimeout) {
    HttpClient client = HttpClient.newBuilder()
        .connectTimeout(connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout)
        .build();
    return request -> client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  static HttpRequest.Builder request(URI uri, Duration readTimeout) {
    Duration timeout = readTimeout == null ? Duration.ofSeconds(120) : readTimeout;
    return HttpRequest.newBuilder(uri).timeout(timeout);
  }
}
