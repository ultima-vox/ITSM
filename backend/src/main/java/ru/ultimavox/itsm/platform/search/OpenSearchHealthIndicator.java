package ru.ultimavox.itsm.platform.search;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/** Actuator health for OpenSearch when {@code itsm.opensearch.url} is set. */
@Component("opensearch")
@Conditional(OpenSearchEnabledCondition.class)
class OpenSearchHealthIndicator implements HealthIndicator {

  private final ItsmOpenSearchProperties props;
  private final OpenSearchHttpClient http;

  @Autowired
  OpenSearchHealthIndicator(ItsmOpenSearchProperties props) {
    this(props, OpenSearchHttpClient.jdk(
        props.getConnectTimeout() == null ? Duration.ofSeconds(2) : props.getConnectTimeout(),
        props.basicAuthorizationHeader()));
  }

  /** Package-private for unit tests. */
  OpenSearchHealthIndicator(ItsmOpenSearchProperties props, OpenSearchHttpClient http) {
    this.props = props;
    this.http = http;
  }

  @Override
  public Health health() {
    String base = props.getUrl().endsWith("/")
        ? props.getUrl().substring(0, props.getUrl().length() - 1)
        : props.getUrl();
    try {
      HttpRequest request = OpenSearchHttpClient.request(
              URI.create(base + "/_cluster/health"),
              props.getReadTimeout() == null ? Duration.ofSeconds(3) : props.getReadTimeout())
          .GET()
          .build();
      HttpResponse<String> response = http.send(request);
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        return Health.up()
            .withDetail("url", props.getUrl())
            .withDetail("index", props.getIndex())
            .withDetail("statusCode", response.statusCode())
            .build();
      }
      return Health.down()
          .withDetail("url", props.getUrl())
          .withDetail("statusCode", response.statusCode())
          .build();
    } catch (Exception ex) {
      return Health.down(ex).withDetail("url", props.getUrl()).build();
    }
  }
}
