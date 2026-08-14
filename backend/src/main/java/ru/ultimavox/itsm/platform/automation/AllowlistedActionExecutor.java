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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executes only allowlisted action types. Unknown types are rejected and logged. Built-in
 * adapters ({@code notify}, {@code log}, {@code index}) run here; business capabilities are
 * provided by registered {@link AutomationActionHandler}s living in the owning modules.
 */
@Component
public class AllowlistedActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(AllowlistedActionExecutor.class);

    private static final Set<String> BUILTIN = Set.of("notify", "log", "index");

    private final NotificationService notifications;
    private final SearchIndexService searchIndex;
    private final Map<String, AutomationActionHandler> handlers;

    public AllowlistedActionExecutor(
            NotificationService notifications,
            SearchIndexService searchIndex,
            List<AutomationActionHandler> handlers) {
        this.notifications = notifications;
        this.searchIndex = searchIndex;
        this.handlers = toMap(handlers);
    }

    private static Map<String, AutomationActionHandler> toMap(List<AutomationActionHandler> handlers) {
        if (handlers == null || handlers.isEmpty()) return Map.of();
        Map<String, AutomationActionHandler> byType = new java.util.HashMap<>();
        for (AutomationActionHandler handler : handlers) {
            if (handler.actionType() == null || handler.actionType().isBlank()) {
                throw new IllegalStateException("Automation action handler must declare an actionType");
            }
            if (BUILTIN.contains(handler.actionType())) {
                throw new IllegalStateException(
                        "Automation action handler clashes with built-in type: " + handler.actionType());
            }
            if (byType.put(handler.actionType(), handler) != null) {
                throw new IllegalStateException(
                        "Duplicate automation action handler for type: " + handler.actionType());
            }
        }
        return Map.copyOf(byType);
    }

    boolean supports(String type) {
        return BUILTIN.contains(type) || handlers.containsKey(type);
    }

    void execute(Action action, DomainEvent event) {
        String type = action.type();
        AutomationActionHandler handler = handlers.get(type);
        if (handler != null) {
            handler.execute(event, action.parameters());
            return;
        }
        if (!BUILTIN.contains(type)) {
            throw new IllegalArgumentException("Action type not allowlisted: " + type);
        }
        switch (type) {
            case "notify" -> executeNotify(action.parameters(), event);
            case "log" -> log.info("Automation log action for event {} payload={}", event.id(), action.parameters());
            case "index" -> executeIndex(action.parameters(), event);
            default -> throw new IllegalArgumentException("Unhandled allowlisted action: " + type);
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
