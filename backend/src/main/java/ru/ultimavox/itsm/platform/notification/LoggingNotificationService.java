package ru.ultimavox.itsm.platform.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Default notification adapter: logs structured delivery intent.
 * Replace with SMTP / push / webhook adapters without changing callers.
 */
@Service
public class LoggingNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationService.class);

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
    }
}
