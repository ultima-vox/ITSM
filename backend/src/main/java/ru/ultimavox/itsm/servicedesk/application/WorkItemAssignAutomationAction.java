package ru.ultimavox.itsm.servicedesk.application;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.ultimavox.itsm.platform.automation.ActionParameterResolver;
import ru.ultimavox.itsm.platform.automation.AutomationActionHandler;
import ru.ultimavox.itsm.platform.event.DomainEvent;

/**
 * Automation action that assigns the triggering work item. Lets rules route requests
 * automatically, e.g. {@code { "type": "assign", "parameters": { "assigneeId": "{{data.requesterId}}" } }}
 * to hand the item back to the requester's support queue.
 */
@Component
public class WorkItemAssignAutomationAction implements AutomationActionHandler {

  static final String TYPE = "assign";

  private final AssignWorkItem assignWorkItem;

  public WorkItemAssignAutomationAction(AssignWorkItem assignWorkItem) {
    this.assignWorkItem = assignWorkItem;
  }

  @Override
  public String actionType() {
    return TYPE;
  }

  @Override
  public void execute(DomainEvent event, Map<String, Object> parameters) {
    if (!"work-item".equals(event.aggregateType())) {
      throw new IllegalArgumentException(
          "assign action requires a work-item aggregate, got " + event.aggregateType());
    }
    String assigneeId = ActionParameterResolver.resolve(parameters, event, "assigneeId");
    if (assigneeId == null || assigneeId.isBlank()) {
      throw new IllegalArgumentException("assign action requires an assigneeId parameter");
    }
    String teamId = ActionParameterResolver.resolve(parameters, event, "teamId");
    assignWorkItem.assign(
        UUID.fromString(event.aggregateId()),
        new AssignWorkItem.Command(assigneeId, teamId),
        event.actorId());
  }
}
