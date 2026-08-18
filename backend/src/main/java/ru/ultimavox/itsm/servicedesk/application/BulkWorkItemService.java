package ru.ultimavox.itsm.servicedesk.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Impact;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Priority;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Urgency;

@Service
public class BulkWorkItemService {
  private static final int MAX_BATCH_SIZE = 200;
  private final AssignWorkItem assignWorkItem;
  private final UpdateWorkItem updateWorkItem;
  private final TransitionWorkItem transitionWorkItem;

  BulkWorkItemService(AssignWorkItem assignWorkItem, UpdateWorkItem updateWorkItem, TransitionWorkItem transitionWorkItem) {
    this.assignWorkItem = assignWorkItem;
    this.updateWorkItem = updateWorkItem;
    this.transitionWorkItem = transitionWorkItem;
  }

  @Transactional
  public Result assign(List<UUID> ids, String assigneeId, String teamId, String actor) {
    List<UUID> uniqueIds = validateIds(ids);
    for (UUID id : uniqueIds) {
      assignWorkItem.assign(id, new AssignWorkItem.Command(assigneeId, teamId), actor);
    }
    return new Result(uniqueIds.size());
  }

  @Transactional
  public Result setPriority(List<UUID> ids, Priority priority, String actor) {
    if (priority == null) {
      throw new IllegalArgumentException("priority is required");
    }
    List<UUID> uniqueIds = validateIds(ids);
    Impact impact = switch (priority) {
      case CRITICAL, HIGH -> Impact.HIGH;
      case MEDIUM -> Impact.MEDIUM;
      case LOW -> Impact.LOW;
    };
    Urgency urgency = switch (priority) {
      case CRITICAL -> Urgency.HIGH;
      case HIGH -> Urgency.MEDIUM;
      case MEDIUM -> Urgency.MEDIUM;
      case LOW -> Urgency.LOW;
    };
    for (UUID id : uniqueIds) {
      updateWorkItem.update(id, new UpdateWorkItem.Command(null, null, null, impact, urgency), actor);
    }
    return new Result(uniqueIds.size());
  }

  @Transactional
  public Result transition(List<UUID> ids, ru.ultimavox.itsm.servicedesk.domain.WorkItem.State targetState,
      String resolutionCode, String resolutionNotes, String actor) {
    List<UUID> uniqueIds = validateIds(ids);
    for (UUID id : uniqueIds) {
      transitionWorkItem.transition(id, new TransitionWorkItem.Command(targetState, resolutionCode, resolutionNotes), actor);
    }
    return new Result(uniqueIds.size());
  }

  private static List<UUID> validateIds(List<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      throw new IllegalArgumentException("ids must not be empty");
    }
    if (ids.size() > MAX_BATCH_SIZE) {
      throw new IllegalArgumentException("Batch size must not exceed " + MAX_BATCH_SIZE);
    }
    if (ids.stream().anyMatch(java.util.Objects::isNull)) {
      throw new IllegalArgumentException("ids must not contain null");
    }
    return List.copyOf(new LinkedHashSet<>(ids));
  }

  public record Result(int updated) {}
}
