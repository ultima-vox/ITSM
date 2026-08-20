package ru.ultimavox.itsm.platform.search;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenSearch HTTP integration. When {@code url} is blank, JDBC / no-op search remains active.
 */
@ConfigurationProperties(prefix = "itsm.opensearch")
public class ItsmOpenSearchProperties {

  /**
   * Base URL, e.g. {@code http://localhost:9200}. Empty disables OpenSearch and keeps JDBC.
   * Bound from {@code OPENSEARCH_URL} in application.yml.
   */
  private String url = "";

  private String index = "itsm";

  /** Optional cluster credentials; required once the security plugin is enabled. */
  private String username = "";

  private String password = "";

  private Duration connectTimeout = Duration.ofSeconds(2);

  private Duration readTimeout = Duration.ofSeconds(5);

  public boolean isConfigured() {
    return url != null && !url.isBlank();
  }

  /** {@code null} when no credentials are configured, so requests stay unauthenticated. */
  public String basicAuthorizationHeader() {
    if (username == null || username.isBlank() || password == null || password.isEmpty()) {
      return null;
    }
    String token = username + ":" + password;
    return "Basic " + Base64.getEncoder()
        .encodeToString(token.getBytes(StandardCharsets.UTF_8));
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getIndex() {
    return index;
  }

  public void setIndex(String index) {
    this.index = index;
  }

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(Duration connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public Duration getReadTimeout() {
    return readTimeout;
  }

  public void setReadTimeout(Duration readTimeout) {
    this.readTimeout = readTimeout;
  }
}
