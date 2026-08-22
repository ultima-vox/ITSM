package ru.ultimavox.itsm.releasemanagement.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Release aggregate with the guarded build → test → go/no-go → deploy lifecycle.
 * The gates below are safety invariants: workflow metadata may add conditions on top,
 * but it can never let a release skip a plan, a test summary, or a go decision.
 */
public record Release(
    UUID id,
    String number,
    String name,
    Type type,
    Status status,
    String description,
    String deploymentPlan,
    String rollbackPlan,
    String testSummary,
    GoDecision goDecision,
    String goDecisionNotes,
    String goDecidedBy,
    Instant goDecidedAt,
    String releaseManager,
    Instant plannedStart,
    Instant plannedEnd,
    Instant actualStart,
    Instant actualEnd,
    long version
) {
  public Release transition(Status target) {
    if (!allowed(status, target)) {
      throw new IllegalStateException("Transition %s -> %s is not allowed".formatted(status, target));
    }
    if (target == Status.TESTING && (blank(deploymentPlan) || blank(rollbackPlan))) {
      throw new IllegalStateException("A deployment plan and a rollback plan are required before testing");
    }
    if (target == Status.GO_NO_GO && blank(testSummary)) {
      throw new IllegalStateException("A test summary is required before the go / no-go review");
    }
    if (target == Status.DEPLOYING && goDecision != GoDecision.GO) {
      throw new IllegalStateException("A recorded GO decision is required before deployment");
    }
    Instant startedAt = target == Status.DEPLOYING && actualStart == null ? Instant.now() : actualStart;
    Instant endedAt = target == Status.DEPLOYED || target == Status.ROLLED_BACK
        ? (actualEnd == null ? Instant.now() : actualEnd)
        : actualEnd;
    return new Release(
        id, number, name, type, target, description, deploymentPlan, rollbackPlan, testSummary,
        goDecision, goDecisionNotes, goDecidedBy, goDecidedAt, releaseManager,
        plannedStart, plannedEnd, startedAt, endedAt, version
    );
  }

  public Release withGoDecision(GoDecision decision, String notes, String decidedBy, Instant decidedAt) {
    if (status != Status.GO_NO_GO) {
      throw new IllegalStateException("The go / no-go decision is only recorded during the GO_NO_GO review");
    }
    return new Release(
        id, number, name, type, status, description, deploymentPlan, rollbackPlan, testSummary,
        decision, notes, decidedBy, decidedAt, releaseManager,
        plannedStart, plannedEnd, actualStart, actualEnd, version
    );
  }

  public boolean terminal() {
    return status == Status.CLOSED || status == Status.CANCELLED;
  }

  /** A release that already left the review gate must not have its content or plans edited. */
  public boolean contentFrozen() {
    return switch (status) {
      case DEPLOYING, DEPLOYED, ROLLED_BACK, CLOSED, CANCELLED -> true;
      default -> false;
    };
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static boolean allowed(Status from, Status to) {
    return switch (from) {
      case PLANNING -> to == Status.BUILD || to == Status.CANCELLED;
      case BUILD -> to == Status.TESTING || to == Status.PLANNING || to == Status.CANCELLED;
      case TESTING -> to == Status.GO_NO_GO || to == Status.BUILD || to == Status.CANCELLED;
      case GO_NO_GO -> to == Status.DEPLOYING || to == Status.BUILD || to == Status.CANCELLED;
      case DEPLOYING -> to == Status.DEPLOYED || to == Status.ROLLED_BACK;
      case DEPLOYED -> to == Status.CLOSED || to == Status.ROLLED_BACK;
      case ROLLED_BACK -> to == Status.PLANNING || to == Status.CLOSED;
      case CLOSED, CANCELLED -> false;
    };
  }

  public enum Type { MAJOR, MINOR, PATCH, EMERGENCY }

  public enum GoDecision { GO, NO_GO }

  public enum Status {
    PLANNING, BUILD, TESTING, GO_NO_GO, DEPLOYING, DEPLOYED, ROLLED_BACK, CLOSED, CANCELLED
  }
}
