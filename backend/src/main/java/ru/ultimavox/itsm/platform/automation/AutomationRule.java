package ru.ultimavox.itsm.platform.automation;
import java.util.*;
/** Declarative automation; actions are resolved from an allowlisted catalog, never arbitrary application code. */
public record AutomationRule(UUID id, String name, boolean enabled, Trigger trigger, List<Condition> conditions, List<Action> actions) {
 public record Trigger(String eventType) { public Trigger { if (!eventType.matches("[a-z][a-z0-9-]*\\.[a-z][a-z0-9-]*")) throw new IllegalArgumentException("Invalid event type"); } }
 public record Condition(String field, Operator operator, String value) {} public enum Operator { EQUALS, NOT_EQUALS, IN, CONTAINS, GREATER_THAN }
 public record Action(String type, Map<String,Object> parameters) {}
}
