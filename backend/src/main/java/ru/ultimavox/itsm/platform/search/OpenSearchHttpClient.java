package ru.ultimavox.itsm.platform.search;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Thin HTTP facade over OpenSearch REST so unit tests can substitute a fake without a live cluster.
 */
public interface OpenSearchHttpClient {

  HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;

  static OpenSearchHttpClient jdk(Duration connectTimeout) {
    return jdk(connectTimeout, null);
  }

  /**
   * @param authorization value for the {@code Authorization} header, or {@code null} for an
   *     unauthenticated cluster. Applied here so no call site can forget it.
   */
  static OpenSearchHttpClient jdk(Duration connectTimeout, String authorization) {
    return jdk(connectTimeout, authorization, null);
  }

  /**
   * @param caCertificatePath PEM certificate authority for the cluster's HTTPS endpoint, or
   *     {@code null}/blank to use the JVM trust store.
   */
  static OpenSearchHttpClient jdk(Duration connectTimeout, String authorization, String caCertificatePath) {
    HttpClient.Builder builder = HttpClient.newBuilder()
        .connectTimeout(connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout);
    SSLContext sslContext = trustStoreFor(caCertificatePath);
    if (sslContext != null) {
      builder.sslContext(sslContext);
    }
    HttpClient client = builder.build();
    return request -> {
      HttpRequest outbound = authorization == null
          ? request
          : HttpRequest.newBuilder(request, (name, value) -> true)
              .header("Authorization", authorization)
              .build();
      return client.send(outbound, HttpResponse.BodyHandlers.ofString());
    };
  }

  /** Trusts exactly the supplied authority; never disables verification. */
  static SSLContext trustStoreFor(String caCertificatePath) {
    if (caCertificatePath == null || caCertificatePath.isBlank()) {
      return null;
    }
    try (InputStream pem = Files.newInputStream(Path.of(caCertificatePath))) {
      Certificate authority = CertificateFactory.getInstance("X.509").generateCertificate(pem);
      KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
      trust.load(null, null);
      trust.setCertificateEntry("opensearch-ca", authority);
      TrustManagerFactory trustManagers =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagers.init(trust);
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, trustManagers.getTrustManagers(), null);
      return context;
    } catch (Exception cause) {
      throw new IllegalStateException(
          "Cannot load OpenSearch CA certificate from " + caCertificatePath, cause);
    }
  }

  static HttpRequest.Builder request(URI uri, Duration readTimeout) {
    Duration timeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
    return HttpRequest.newBuilder(uri).timeout(timeout);
  }
}
