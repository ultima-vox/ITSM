package ru.ultimavox.itsm.platform.automation;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Declarative automation rule: WHEN event IF conditions THEN actions.
 * Actions are resolved from an allowlisted catalog, never arbitrary application code.
 */
public record AutomationRule(
        UUID id,
        String ruleKey,
        String name,
        int version,
        boolean enabled,
        Trigger trigger,
        List<Condition> conditions,
        List<Action> actions
) {
    public AutomationRule {
        if (ruleKey == null || ruleKey.isBlank()) {
            throw new IllegalArgumentException("ruleKey is required");
        }
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public record Trigger(String eventType) {
        public Trigger {
            if (eventType == null || !eventType.matches("[a-z][a-z0-9-]*\\.[a-z][a-z0-9-]*")) {
                throw new IllegalArgumentException("Invalid event type: " + eventType);
            }
        }
    }

    public record Condition(String field, Operator operator, String value) {}

    public enum Operator {
        EQUALS, NOT_EQUALS, IN, CONTAINS, GREATER_THAN
    }

    public record Action(String type, Map<String, Object> parameters) {
        public Action {
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }
}
