package ru.ultimavox.itsm.servicedesk.domain;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Service Desk work-item aggregate (incident or service request).
 * Lifecycle and priority derivation live here; persistence stays in the application layer.
 */
public record WorkItem(
    UUID id,
    String number,
    Type type,
    String title,
    String description,
    String service,
    State state,
    Priority priority,
    Impact impact,
    Urgency urgency,
    String assigneeId,
    String requesterId,
    String teamId,
    String resolutionCode,
    String resolutionNotes,
    Instant createdAt,
    Instant updatedAt,
    Instant closedAt
) {

  public WorkItem {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(number, "number");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(description, "description");
    Objects.requireNonNull(service, "service");
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(priority, "priority");
    Objects.requireNonNull(impact, "impact");
    Objects.requireNonNull(urgency, "urgency");
    Objects.requireNonNull(requesterId, "requesterId");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
  }

  /** ITIL-style priority matrix: impact 1 + urgency 1 → CRITICAL. */
  public static Priority derivePriority(Impact impact, Urgency urgency) {
    Objects.requireNonNull(impact, "impact");
    Objects.requireNonNull(urgency, "urgency");
    return switch (impact) {
      case HIGH -> switch (urgency) {
        case HIGH -> Priority.CRITICAL;
        case MEDIUM -> Priority.HIGH;
        case LOW -> Priority.MEDIUM;
      };
      case MEDIUM -> switch (urgency) {
        case HIGH -> Priority.HIGH;
        case MEDIUM -> Priority.MEDIUM;
        case LOW -> Priority.LOW;
      };
      case LOW -> switch (urgency) {
        case HIGH -> Priority.MEDIUM;
        case MEDIUM, LOW -> Priority.LOW;
      };
    };
  }

  public WorkItem withDetails(
      String newTitle,
      String newDescription,
      String newService,
      Impact newImpact,
      Urgency newUrgency,
      Instant now
  ) {
    Priority newPriority = derivePriority(newImpact, newUrgency);
    return new WorkItem(
        id, number, type, newTitle, newDescription, newService, state, newPriority,
        newImpact, newUrgency, assigneeId, requesterId, teamId,
        resolutionCode, resolutionNotes, createdAt, now, closedAt
    );
  }

  public WorkItem assign(String newAssigneeId, String newTeamId, Instant now) {
    return new WorkItem(
        id, number, type, title, description, service, state, priority,
        impact, urgency, newAssigneeId, requesterId, newTeamId,
        resolutionCode, resolutionNotes, createdAt, now, closedAt
    );
  }

  public WorkItem transition(
      State target,
      String newResolutionCode,
      String newResolutionNotes,
      Instant now
  ) {
    if (!allowedTransition(state, target)) {
      throw new IllegalStateException(
          "Transition %s -> %s is not allowed for work item %s".formatted(state, target, number)
      );
    }
    if (target == State.RESOLVED || target == State.CLOSED) {
      if (newResolutionCode == null || newResolutionCode.isBlank()) {
        throw new IllegalArgumentException("resolutionCode is required when resolving or closing");
      }
    }
    Instant newClosedAt = (target == State.CLOSED || target == State.CANCELLED) ? now : closedAt;
    String code = newResolutionCode != null ? newResolutionCode : resolutionCode;
    String notes = newResolutionNotes != null ? newResolutionNotes : resolutionNotes;
    return new WorkItem(
        id, number, type, title, description, service, target, priority,
        impact, urgency, assigneeId, requesterId, teamId,
        code, notes, createdAt, now, newClosedAt
    );
  }

  public boolean isOpen() {
    return state != State.CLOSED && state != State.CANCELLED;
  }

  static boolean allowedTransition(State from, State to) {
    return allowedTargets(from).contains(to);
  }

  static Set<State> allowedTargets(State from) {
    return switch (from) {
      case NEW -> EnumSet.of(State.IN_PROGRESS, State.CANCELLED);
      case IN_PROGRESS -> EnumSet.of(State.PENDING, State.RESOLVED, State.CANCELLED);
      case PENDING -> EnumSet.of(State.IN_PROGRESS, State.RESOLVED, State.CANCELLED);
      case RESOLVED -> EnumSet.of(State.CLOSED, State.IN_PROGRESS);
      case CLOSED, CANCELLED -> EnumSet.noneOf(State.class);
    };
  }

  public enum Type {
    INCIDENT,
    SERVICE_REQUEST
  }

  public enum State {
    NEW,
    IN_PROGRESS,
    PENDING,
    RESOLVED,
    CLOSED,
    CANCELLED
  }

  public enum Priority {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
  }

  /** Level 1 = highest business impact. */
  public enum Impact {
    HIGH,
    MEDIUM,
    LOW
  }

  /** Level 1 = highest urgency. */
  public enum Urgency {
    HIGH,
    MEDIUM,
    LOW
  }
}
