package ru.ultimavox.itsm.platform.sla;
import java.time.Duration; import java.util.*;
/** SLA policy definition. Calendars, pausing states and milestones are explicit and auditable. */
public record SlaPolicy(UUID id, String key, String calendarKey, List<Target> targets, Set<String> pauseStates) { public record Target(String metric, String condition, Duration target, Duration warningBefore) {} }
