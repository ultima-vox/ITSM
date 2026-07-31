package ru.ultimavox.itsm.changemanagement.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Change aggregate with an explicit guarded lifecycle.
 * Workflow metadata may add conditions but cannot bypass these safety invariants.
 */
public record Change(
    UUID id,
    String number,
    Type type,
    Risk risk,
    Status status,
    String title,
    Instant plannedStart,
    Instant plannedEnd,
    String implementationPlan,
    String rollbackPlan,
    String businessJustification,
    String cabNotes,
    Risk cabRiskLevel
) {
  public Change transition(Status target) {
    if (!allowed(status, target)) {
      throw new IllegalStateException("Transition %s -> %s is not allowed".formatted(status, target));
    }
    if (target == Status.SCHEDULED && status != Status.APPROVED) {
      throw new IllegalStateException("An approved change is required before scheduling");
    }
    return new Change(
        id, number, type, risk, target, title, plannedStart, plannedEnd,
        implementationPlan, rollbackPlan, businessJustification, cabNotes, cabRiskLevel
    );
  }

  public Change withCabAssessment(String notes, Risk assessedRisk) {
    if (status == Status.CLOSED || status == Status.REJECTED) {
      throw new IllegalStateException("CAB assessment cannot change a terminal change");
    }
    return new Change(
        id, number, type, risk, status, title, plannedStart, plannedEnd,
        implementationPlan, rollbackPlan, businessJustification, notes, assessedRisk
    );
  }

  private static boolean allowed(Status from, Status to) {
    return switch (from) {
      case DRAFT -> to == Status.SUBMITTED || to == Status.REJECTED;
      case SUBMITTED -> to == Status.CAB_REVIEW || to == Status.REJECTED;
      case CAB_REVIEW -> to == Status.APPROVED || to == Status.REJECTED || to == Status.SUBMITTED;
      case APPROVED -> to == Status.SCHEDULED || to == Status.REJECTED;
      case SCHEDULED -> to == Status.IMPLEMENTING || to == Status.REJECTED;
      case IMPLEMENTING -> to == Status.REVIEW;
      case REVIEW -> to == Status.CLOSED || to == Status.IMPLEMENTING;
      case CLOSED, REJECTED -> false;
    };
  }

  public enum Type { STANDARD, NORMAL, EMERGENCY }

  public enum Risk { LOW, MEDIUM, HIGH, CRITICAL }

  public enum Status {
    DRAFT, SUBMITTED, CAB_REVIEW, APPROVED, SCHEDULED, IMPLEMENTING, REVIEW, CLOSED, REJECTED
  }
}
