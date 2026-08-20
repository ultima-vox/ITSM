package ru.ultimavox.itsm.platform.search;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class OpenSearchAuthenticationTest {

  @Test
  void noCredentialsLeaveRequestsUnauthenticated() {
    ItsmOpenSearchProperties props = new ItsmOpenSearchProperties();
    props.setUrl("http://opensearch:9200");

    assertThat(props.basicAuthorizationHeader()).isNull();
  }

  @Test
  void credentialsProduceABasicHeader() {
    ItsmOpenSearchProperties props = new ItsmOpenSearchProperties();
    props.setUsername("itsm-search");
    props.setPassword("s3cret");

    String expected = "Basic " + Base64.getEncoder()
        .encodeToString("itsm-search:s3cret".getBytes(StandardCharsets.UTF_8));
    assertThat(props.basicAuthorizationHeader()).isEqualTo(expected);
  }

  @Test
  void aBlankPasswordIsNotCredentials() {
    ItsmOpenSearchProperties props = new ItsmOpenSearchProperties();
    props.setUsername("itsm-search");
    props.setPassword("");

    assertThat(props.basicAuthorizationHeader()).isNull();
  }

  @Test
  void noCaCertificateKeepsTheDefaultTrustStore() {
    assertThat(OpenSearchHttpClient.trustStoreFor(null)).isNull();
    assertThat(OpenSearchHttpClient.trustStoreFor("  ")).isNull();
  }

  @Test
  void aMissingCaCertificateFailsLoudly() {
    assertThatThrownBy(() -> OpenSearchHttpClient.trustStoreFor("/nonexistent/ca.pem"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cannot load OpenSearch CA certificate");
  }

  @Test
  void clientAttachesTheHeaderToEveryRequest() throws Exception {
    HttpRequest original = HttpRequest.newBuilder(URI.create("http://opensearch:9200/itsm/_search"))
        .timeout(Duration.ofSeconds(5))
        .GET()
        .build();

    HttpRequest authenticated = HttpRequest.newBuilder(original, (name, value) -> true)
        .header("Authorization", "Basic dGVzdDp0ZXN0")
        .build();

    assertThat(original.headers().firstValue("Authorization")).isEmpty();
    assertThat(authenticated.headers().firstValue("Authorization")).contains("Basic dGVzdDp0ZXN0");
    assertThat(authenticated.uri()).isEqualTo(original.uri());
  }
}
