package ru.ultimavox.itsm.platform.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Optional outbound webhook for EMAIL/WEBHOOK channels (and IN_APP fan-out when configured).
 * Disabled unless {@code itsm.notifications.webhook.url} is set.
 */
@Component
@ConditionalOnProperty(prefix = "itsm.notifications.webhook", name = "url")
public class WebhookNotificationAdapter {

  private static final Logger log = LoggerFactory.getLogger(WebhookNotificationAdapter.class);

  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(3))
      .build();
  private final ObjectMapper json;
  private final URI endpoint;
  private final Duration timeout;

  public WebhookNotificationAdapter(
      ObjectMapper json,
      @Value("${itsm.notifications.webhook.url}") String url,
      @Value("${itsm.notifications.webhook.timeout:PT3S}") Duration timeout
  ) {
    this.json = json;
    this.endpoint = URI.create(url);
    this.timeout = timeout == null ? Duration.ofSeconds(3) : timeout;
  }

  public void deliver(NotificationRequest request, StoredNotification stored) {
    try {
      Map<String, Object> body = Map.of(
          "id", stored.id().toString(),
          "templateKey", request.templateKey(),
          "channel", request.channel().name(),
          "recipientSubject", request.recipientSubject(),
          "locale", request.locale() == null ? "ru" : request.locale(),
          "correlationId", request.correlationId() == null ? "" : request.correlationId().toString(),
          "variables", request.variables(),
          "entityType", stored.entityType() == null ? "" : stored.entityType(),
          "entityId", stored.entityId() == null ? "" : stored.entityId()
      );
      byte[] payload = json.writeValueAsBytes(body);
      HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
          .timeout(timeout)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
          .build();
      HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 300) {
        log.warn(
            "notification webhook non-2xx status={} template={} recipient={}",
            response.statusCode(),
            request.templateKey(),
            request.recipientSubject()
        );
      }
    } catch (Exception ex) {
      log.warn(
          "notification webhook failed template={} recipient={}: {}",
          request.templateKey(),
          request.recipientSubject(),
          ex.toString()
      );
    }
  }
}
