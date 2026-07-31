package ru.ultimavox.itsm.problemmanagement.domain;

import java.util.Set;
import java.util.UUID;

/**
 * Problem aggregate keeps root cause and workaround separate from individual incidents.
 */
public record Problem(
    UUID id,
    String number,
    String title,
    Status status,
    String rootCause,
    String workaround,
    Set<UUID> linkedWorkItems
) {
  public Problem {
    linkedWorkItems = linkedWorkItems == null ? Set.of() : Set.copyOf(linkedWorkItems);
  }

  public Problem transition(Status target) {
    if (!allowed(status, target)) {
      throw new IllegalStateException("Transition %s -> %s is not allowed".formatted(status, target));
    }
    if (target == Status.ROOT_CAUSE_IDENTIFIED && (rootCause == null || rootCause.isBlank())) {
      throw new IllegalStateException("Root cause is required before ROOT_CAUSE_IDENTIFIED");
    }
    if (target == Status.KNOWN_ERROR && (workaround == null || workaround.isBlank())) {
      throw new IllegalStateException("Workaround is required before KNOWN_ERROR");
    }
    return new Problem(id, number, title, target, rootCause, workaround, linkedWorkItems);
  }

  public Problem withInvestigationNotes(String rootCause, String workaround) {
    return new Problem(
        id,
        number,
        title,
        status,
        rootCause != null ? rootCause : this.rootCause,
        workaround != null ? workaround : this.workaround,
        linkedWorkItems
    );
  }

  public Problem linkWorkItem(UUID workItemId) {
    if (workItemId == null) {
      throw new IllegalArgumentException("workItemId is required");
    }
    var next = new java.util.HashSet<>(linkedWorkItems);
    next.add(workItemId);
    return new Problem(id, number, title, status, rootCause, workaround, next);
  }

  private static boolean allowed(Status from, Status to) {
    return switch (from) {
      case NEW -> to == Status.UNDER_INVESTIGATION;
      case UNDER_INVESTIGATION -> to == Status.ROOT_CAUSE_IDENTIFIED || to == Status.KNOWN_ERROR;
      case ROOT_CAUSE_IDENTIFIED -> to == Status.KNOWN_ERROR || to == Status.RESOLVED;
      case KNOWN_ERROR -> to == Status.RESOLVED;
      case RESOLVED -> to == Status.CLOSED;
      case CLOSED -> false;
    };
  }

  public enum Status {
    NEW, UNDER_INVESTIGATION, ROOT_CAUSE_IDENTIFIED, KNOWN_ERROR, RESOLVED, CLOSED
  }
}
