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
            Set<String> requiredFields
    ) {
        public Transition {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Transition key is required");
            }
            requiredPermissions = requiredPermissions == null ? Set.of() : Set.copyOf(requiredPermissions);
            requiredFields = requiredFields == null ? Set.of() : Set.copyOf(requiredFields);
        }
    }
}
