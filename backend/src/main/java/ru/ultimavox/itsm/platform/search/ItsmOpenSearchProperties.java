package ru.ultimavox.itsm.platform.search;

import java.time.Duration;
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

  private Duration connectTimeout = Duration.ofSeconds(2);

  private Duration readTimeout = Duration.ofSeconds(5);

  public boolean isConfigured() {
    return url != null && !url.isBlank();
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
