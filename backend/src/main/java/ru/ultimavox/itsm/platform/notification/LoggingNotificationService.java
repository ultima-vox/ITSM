package ru.ultimavox.itsm.platform.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Default notification adapter: logs structured delivery intent and persists
 * via {@link NotificationStore} (PostgreSQL in production).
 * Optionally fans out to {@link WebhookNotificationAdapter} when configured.
 */
@Service
public class LoggingNotificationService implements NotificationService {

  private static final Logger log = LoggerFactory.getLogger(LoggingNotificationService.class);

  private final NotificationStore store;
  private final ObjectProvider<WebhookNotificationAdapter> webhook;

  @Autowired
  public LoggingNotificationService(
      NotificationStore store,
      ObjectProvider<WebhookNotificationAdapter> webhook
  ) {
    this.store = store;
    this.webhook = webhook;
  }

  /** Unit-test constructor without webhook adapter. */
  LoggingNotificationService(NotificationStore store) {
    this.store = store;
    this.webhook = new EmptyWebhookProvider();
  }

  @Override
  public void send(NotificationRequest request) {
    log.info(
        "notification channel={} template={} recipient={} locale={} correlationId={} variables={}",
        request.channel(),
        request.templateKey(),
        request.recipientSubject(),
        request.locale(),
        request.correlationId(),
        request.variables().keySet()
    );
    StoredNotification stored = store.save(StoredNotification.from(request));
    WebhookNotificationAdapter adapter = webhook.getIfAvailable();
    if (adapter != null && request.channel() != NotificationRequest.Channel.IN_APP) {
      adapter.deliver(request, stored);
    }
  }

  /** Minimal ObjectProvider for unit tests (no Spring context). */
  private static final class EmptyWebhookProvider implements ObjectProvider<WebhookNotificationAdapter> {
    @Override
    public WebhookNotificationAdapter getObject() {
      return null;
    }

    @Override
    public WebhookNotificationAdapter getObject(Object... args) {
      return null;
    }

    @Override
    public WebhookNotificationAdapter getIfAvailable() {
      return null;
    }

    @Override
    public WebhookNotificationAdapter getIfUnique() {
      return null;
    }
  }
}
