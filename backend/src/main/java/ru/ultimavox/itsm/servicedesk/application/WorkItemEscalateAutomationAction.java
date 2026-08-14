package ru.ultimavox.itsm.servicedesk.application;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.ultimavox.itsm.platform.automation.ActionParameterResolver;
import ru.ultimavox.itsm.platform.automation.AutomationActionHandler;
import ru.ultimavox.itsm.platform.event.DomainEvent;

/**
 * Automation action that escalates a work item whose SLA has breached. Triggered by an
 * {@code sla.breached} event (e.g. via the default rule {@code sla.escalate.breach}): the
 * escalated item is raised to HIGH/HIGH priority, flagged, moved to IN_PROGRESS if still NEW,
 * reindexed and its assignee is notified. The work item is read from the {@code workItemId}
 * parameter or, when absent, from the event payload's {@code aggregateId}.
 */
@Component
public class WorkItemEscalateAutomationAction implements AutomationActionHandler {

  static final String TYPE = "escalate";

  private final EscalateWorkItem escalateWorkItem;

  public WorkItemEscalateAutomationAction(EscalateWorkItem escalateWorkItem) {
    this.escalateWorkItem = escalateWorkItem;
  }

  @Override
  public String actionType() {
    return TYPE;
  }

  @Override
  public void execute(DomainEvent event, Map<String, Object> parameters) {
    if (!"sla.breached".equals(event.type())) {
      throw new IllegalArgumentException(
          "escalate action requires an sla.breached event, got " + event.type());
    }
    String workItemId = ActionParameterResolver.resolve(parameters, event, "workItemId");
    if (workItemId == null || workItemId.isBlank()) {
      Object aggregateId = event.data().get("aggregateId");
      workItemId = aggregateId == null ? null : String.valueOf(aggregateId);
    }
    if (workItemId == null || workItemId.isBlank()) {
      throw new IllegalArgumentException("escalate action requires a workItemId parameter");
    }
    String actorId = ActionParameterResolver.resolve(parameters, event, "actorId");
    escalateWorkItem.escalate(
        UUID.fromString(workItemId),
        actorId == null || actorId.isBlank() ? event.actorId() : actorId);
  }
}
