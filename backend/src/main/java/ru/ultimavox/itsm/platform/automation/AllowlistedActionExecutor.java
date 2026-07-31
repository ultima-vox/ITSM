package ru.ultimavox.itsm.platform.automation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.ultimavox.itsm.platform.automation.AutomationRule.Action;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.notification.NotificationRequest;
import ru.ultimavox.itsm.platform.notification.NotificationService;
import ru.ultimavox.itsm.platform.search.SearchDocument;
import ru.ultimavox.itsm.platform.search.SearchIndexService;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Executes only allowlisted action types. Unknown types are rejected and logged.
 */
@Component
class AllowlistedActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(AllowlistedActionExecutor.class);

    private static final Set<String> ALLOWED = Set.of("notify", "log", "index");

    private final NotificationService notifications;
    private final SearchIndexService searchIndex;

    AllowlistedActionExecutor(NotificationService notifications, SearchIndexService searchIndex) {
        this.notifications = notifications;
        this.searchIndex = searchIndex;
    }

    void execute(Action action, DomainEvent event) {
        if (!ALLOWED.contains(action.type())) {
            throw new IllegalArgumentException("Action type not allowlisted: " + action.type());
        }
        switch (action.type()) {
            case "notify" -> executeNotify(action.parameters(), event);
            case "log" -> log.info("Automation log action for event {} payload={}", event.id(), action.parameters());
            case "index" -> executeIndex(action.parameters(), event);
            default -> throw new IllegalArgumentException("Unhandled allowlisted action: " + action.type());
        }
    }

    private void executeNotify(Map<String, Object> parameters, DomainEvent event) {
        String templateKey = stringParam(parameters, "templateKey", "automation.default");
        String channelName = stringParam(parameters, "channel", "IN_APP");
        String recipient = stringParam(parameters, "recipientSubject", event.aggregateId());
        String locale = stringParam(parameters, "locale", "ru");

        NotificationRequest.Channel channel = NotificationRequest.Channel.valueOf(channelName);
        notifications.send(new NotificationRequest(
                event.correlationId() != null ? event.correlationId() : event.id(),
                templateKey,
                recipient,
                locale,
                event.data() == null ? Map.of() : event.data(),
                channel
        ));
    }

    private void executeIndex(Map<String, Object> parameters, DomainEvent event) {
        String title = stringParam(parameters, "title", event.type() + " " + event.aggregateId());
        String body = stringParam(parameters, "body", "");
        searchIndex.index(new SearchDocument(
                event.aggregateType() + ":" + event.aggregateId(),
                event.aggregateType(),
                title,
                body,
                Set.of(),
                Instant.now(),
                event.data() == null ? Map.of() : event.data()
        ));
    }

    private static String stringParam(Map<String, Object> parameters, String key, String defaultValue) {
        Object value = parameters.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }
}
