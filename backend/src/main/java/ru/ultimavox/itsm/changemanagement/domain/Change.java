package ru.ultimavox.itsm.changemanagement.domain;
import java.time.Instant; import java.util.*;
/** Change aggregate with explicit guarded lifecycle; workflow metadata may add conditions but cannot bypass these safety invariants. */
public record Change(UUID id, String number, Type type, Risk risk, Status status, Instant plannedStart, Instant plannedEnd, String implementationPlan, String rollbackPlan) {
 public Change transition(Status target, boolean approvalGranted) { if(!allowed(status,target)) throw new IllegalStateException("Transition %s -> %s is not allowed".formatted(status,target)); if(target==Status.SCHEDULED&&!approvalGranted) throw new IllegalStateException("An approved change is required before scheduling"); return new Change(id,number,type,risk,target,plannedStart,plannedEnd,implementationPlan,rollbackPlan); }
 private static boolean allowed(Status from,Status to){return switch(from){case DRAFT->to==Status.ASSESSMENT||to==Status.CANCELLED;case ASSESSMENT->to==Status.AUTHORIZATION||to==Status.DRAFT||to==Status.CANCELLED;case AUTHORIZATION->to==Status.SCHEDULED||to==Status.ASSESSMENT||to==Status.CANCELLED;case SCHEDULED->to==Status.IMPLEMENTING||to==Status.CANCELLED;case IMPLEMENTING->to==Status.REVIEW;case REVIEW->to==Status.CLOSED||to==Status.IMPLEMENTING;case CLOSED,CANCELLED->false;};}
 public enum Type { STANDARD, NORMAL, EMERGENCY } public enum Risk { LOW, MEDIUM, HIGH, CRITICAL } public enum Status { DRAFT, ASSESSMENT, AUTHORIZATION, SCHEDULED, IMPLEMENTING, REVIEW, CLOSED, CANCELLED }
}
