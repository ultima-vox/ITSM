package ru.ultimavox.itsm.problemmanagement.domain;
import java.util.*;
/** Problem aggregate keeps root cause and workaround separate from individual incidents. */
public record Problem(UUID id, String number, String title, Status status, String rootCause, String workaround, Set<UUID> linkedIncidents) { public enum Status { NEW, INVESTIGATING, KNOWN_ERROR, RESOLVED, CLOSED } }
