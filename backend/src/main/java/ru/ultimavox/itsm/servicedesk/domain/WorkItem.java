package ru.ultimavox.itsm.servicedesk.domain;
import java.time.Instant; import java.util.UUID;
/** Aggregate owned by Service Desk. Persistence and workflow will live behind this module boundary. */
public record WorkItem(UUID id, String number, String type, String title, Priority priority, String assignee, Instant updatedAt) { public enum Priority { CRITICAL, HIGH, MEDIUM, LOW } }
