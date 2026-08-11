package ru.ultimavox.itsm.problemmanagement.domain;

import java.util.Set;
import java.util.UUID;

/**
 * Problem aggregate keeps root cause, workaround, and resolution separate from incidents.
 */
public record Problem(
    UUID id,
    String number,
    String title,
    Status status,
    String rootCause,
    String workaround,
    String resolution,
    Set<UUID> linkedWorkItems,
    long version
) {
  public Problem {
    linkedWorkItems = linkedWorkItems == null ? Set.of() : Set.copyOf(linkedWorkItems);
  }

  /** Backward-compatible constructor without resolution. */
  public Problem(
      UUID id,
      String number,
      String title,
      Status status,
      String rootCause,
      String workaround,
      Set<UUID> linkedWorkItems
  ) {
    this(id, number, title, status, rootCause, workaround, null, linkedWorkItems, 0);
  }

  public Problem(
      UUID id,
      String number,
      String title,
      Status status,
      String rootCause,
      String workaround,
      String resolution,
      Set<UUID> linkedWorkItems
  ) {
    this(id, number, title, status, rootCause, workaround, resolution, linkedWorkItems, 0);
  }

  public Problem transition(Status target) {
    if (!allowed(status, target)) {
      throw new IllegalStateException("Transition %s -> %s is not allowed".formatted(status, target));
    }
    if (target == Status.ROOT_CAUSE_IDENTIFIED && isBlank(rootCause)) {
      throw new IllegalStateException("Root cause is required before ROOT_CAUSE_IDENTIFIED");
    }
    if (target == Status.KNOWN_ERROR && isBlank(workaround)) {
      throw new IllegalStateException("Workaround is required before KNOWN_ERROR");
    }
    if (target == Status.RESOLVED) {
      if (isBlank(rootCause)) {
        throw new IllegalStateException("Root cause is required before RESOLVED");
      }
      if (isBlank(resolution)) {
        throw new IllegalStateException("Resolution is required before RESOLVED");
      }
    }
    return new Problem(id, number, title, target, rootCause, workaround, resolution, linkedWorkItems, version);
  }

  public Problem withInvestigationNotes(String rootCause, String workaround) {
    return withInvestigationNotes(rootCause, workaround, null);
  }

  public Problem withInvestigationNotes(String rootCause, String workaround, String resolution) {
    return new Problem(
        id,
        number,
        title,
        status,
        rootCause != null ? rootCause : this.rootCause,
        workaround != null ? workaround : this.workaround,
        resolution != null ? resolution : this.resolution,
        linkedWorkItems,
        version
    );
  }

  public Problem linkWorkItem(UUID workItemId) {
    if (workItemId == null) {
      throw new IllegalArgumentException("workItemId is required");
    }
    var next = new java.util.HashSet<>(linkedWorkItems);
    next.add(workItemId);
    return new Problem(id, number, title, status, rootCause, workaround, resolution, next, version);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
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
