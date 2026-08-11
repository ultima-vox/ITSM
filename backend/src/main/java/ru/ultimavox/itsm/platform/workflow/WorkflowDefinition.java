package ru.ultimavox.itsm.platform.workflow;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic workflow contract; transition execution is authorized, validated,
 * audited and outboxed atomically by {@link WorkflowEngine}.
 */
public record WorkflowDefinition(
        UUID id,
        String objectKey,
        int version,
        String initialState,
        Set<String> states,
        List<Transition> transitions
) {
    public WorkflowDefinition {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey is required");
        }
        if (initialState == null || initialState.isBlank()) {
            throw new IllegalArgumentException("initialState is required");
        }
        states = states == null ? Set.of() : Set.copyOf(states);
        transitions = transitions == null ? List.of() : List.copyOf(transitions);
        if (!states.contains(initialState)) {
            throw new IllegalArgumentException("initialState must be a declared state");
        }
    }

    public Optional<Transition> findTransition(String transitionKey) {
        return transitions.stream().filter(t -> t.key().equals(transitionKey)).findFirst();
    }

    public List<Transition> transitionsFrom(String state) {
        return transitions.stream().filter(t -> t.from().equals(state)).toList();
    }

    public record Transition(
            String key,
            String from,
            String to,
            Set<String> requiredPermissions,
            Set<String> requiredFields,
            List<Condition> conditions,
            ApprovalRequirement approval,
            TimerRequirement timer
    ) {
        public Transition(String key, String from, String to,
                          Set<String> requiredPermissions, Set<String> requiredFields) {
            this(key, from, to, requiredPermissions, requiredFields, List.of(), null, null);
        }

        public Transition(String key, String from, String to,
                          Set<String> requiredPermissions, Set<String> requiredFields,
                          ApprovalRequirement approval) {
            this(key, from, to, requiredPermissions, requiredFields, List.of(), approval, null);
        }

        public Transition(String key, String from, String to,
                          Set<String> requiredPermissions, Set<String> requiredFields,
                          ApprovalRequirement approval, TimerRequirement timer) {
            this(key, from, to, requiredPermissions, requiredFields, List.of(), approval, timer);
        }

        public Transition {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Transition key is required");
            }
            requiredPermissions = requiredPermissions == null ? Set.of() : Set.copyOf(requiredPermissions);
            requiredFields = requiredFields == null ? Set.of() : Set.copyOf(requiredFields);
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
            if (timer != null && (approval != null || !requiredPermissions.isEmpty()
                    || !requiredFields.isEmpty() || !conditions.isEmpty())) {
                throw new IllegalArgumentException(
                        "Timer transition cannot require actor permissions, fields, conditions, or approval");
            }
        }
    }

    public record ApprovalRequirement(ApprovalMode mode, Set<String> voterRoles, Integer quorum) {
        public ApprovalRequirement {
            if (mode == null) throw new IllegalArgumentException("Approval mode is required");
            voterRoles = voterRoles == null ? Set.of() : Set.copyOf(voterRoles);
            if (voterRoles.isEmpty()) throw new IllegalArgumentException("Approval voterRoles are required");
            if (mode == ApprovalMode.QUORUM && (quorum == null || quorum < 1)) {
                throw new IllegalArgumentException("Positive quorum is required for QUORUM approval");
            }
            if (mode != ApprovalMode.QUORUM) quorum = null;
        }
    }

    public enum ApprovalMode { ANY, ALL, QUORUM }

    public record Condition(String field, ConditionOperator operator, Object value) {
        public Condition {
            if (field == null || !field.matches("[A-Za-z][A-Za-z0-9_-]*(\\.[A-Za-z][A-Za-z0-9_-]*){0,7}")) {
                throw new IllegalArgumentException("Invalid workflow condition field: " + field);
            }
            if (operator == null) throw new IllegalArgumentException("Workflow condition operator is required");
            if (operator == ConditionOperator.EXISTS && !(value instanceof Boolean)) {
                throw new IllegalArgumentException("EXISTS condition requires boolean value");
            }
            if (operator != ConditionOperator.EXISTS && value == null) {
                throw new IllegalArgumentException("Workflow condition value is required");
            }
        }
    }

    public enum ConditionOperator { EQUALS, NOT_EQUALS, IN, CONTAINS, EXISTS, GT, GTE, LT, LTE }

    public record TimerRequirement(long delaySeconds, int maxAttempts) {
        public TimerRequirement {
            if (delaySeconds < 1) throw new IllegalArgumentException("Timer delaySeconds must be positive");
            if (maxAttempts < 1 || maxAttempts > 20) {
                throw new IllegalArgumentException("Timer maxAttempts must be between 1 and 20");
            }
        }
    }
}
