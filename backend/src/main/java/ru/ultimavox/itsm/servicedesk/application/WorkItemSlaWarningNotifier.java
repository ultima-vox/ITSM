package ru.ultimavox.itsm.servicedesk.application;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.ultimavox.itsm.platform.automation.ActionParameterResolver;
import ru.ultimavox.itsm.platform.automation.AutomationActionHandler;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.notification.NotificationRequest;
import ru.ultimavox.itsm.platform.notification.NotificationService;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;

/**
 * Automation action that notifies the owner of a work item when its SLA enters the warning
 * window. Triggered by an {@code sla.warning} event (e.g. via the default rule
 * {@code sla.warning.notify}): the assignee — or the requester when unassigned — receives an
 * in-app notification carrying the item number/title and the deadline. The work item is read from
 * the {@code workItemId} parameter or, when absent, from the event payload's {@code aggregateId}.
 * Items with neither an assignee nor a requester are skipped.
 */
@Component
public class WorkItemSlaWarningNotifier implements AutomationActionHandler {

  static final String TYPE = "sla-warning-notify";
  static final String TEMPLATE_KEY = "sla.warning";

  private final WorkItemStore store;
  private final NotificationService notifications;

  public WorkItemSlaWarningNotifier(WorkItemStore store, NotificationService notifications) {
    this.store = store;
    this.notifications = notifications;
  }

  @Override
  public String actionType() {
    return TYPE;
  }

  @Override
  public void execute(DomainEvent event, Map<String, Object> parameters) {
    if (!"sla.warning".equals(event.type())) {
      throw new IllegalArgumentException(
          "sla-warning-notify action requires an sla.warning event, got " + event.type());
    }
    String workItemId = ActionParameterResolver.resolve(parameters, event, "workItemId");
    if (workItemId == null || workItemId.isBlank()) {
      Object aggregateId = event.data().get("aggregateId");
      workItemId = aggregateId == null ? null : String.valueOf(aggregateId);
    }
    if (workItemId == null || workItemId.isBlank()) {
      throw new IllegalArgumentException("sla-warning-notify action requires a workItemId parameter");
    }
    WorkItem item = store.requireById(UUID.fromString(workItemId));
    String recipient = recipient(item);
    if (recipient == null) {
      return;
    }
    notifications.send(new NotificationRequest(
        event.correlationId(),
        TEMPLATE_KEY,
        recipient,
        "ru",
        variables(item, event),
        NotificationRequest.Channel.IN_APP
    ));
  }

  private static Map<String, Object> variables(WorkItem item, DomainEvent event) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("workItemId", item.id().toString());
    variables.put("number", item.number());
    variables.put("title", item.title());
    variables.put("state", item.state().name());
    Object dueAt = event.data().get("dueAt");
    if (dueAt != null) {
      variables.put("dueAt", dueAt);
    }
    return variables;
  }

  private static String recipient(WorkItem item) {
    if (item.assigneeId() != null && !item.assigneeId().isBlank()) {
      return item.assigneeId();
    }
    if (item.requesterId() != null && !item.requesterId().isBlank()) {
      return item.requesterId();
    }
    return null;
  }
}
