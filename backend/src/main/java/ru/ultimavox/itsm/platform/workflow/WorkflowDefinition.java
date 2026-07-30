package ru.ultimavox.itsm.platform.workflow;
import java.util.*;
/** Deterministic workflow contract; transition execution is authorized, validated, audited and outboxed atomically. */
public record WorkflowDefinition(UUID id, String objectKey, String initialState, Set<String> states, List<Transition> transitions) {
 public record Transition(String key, String from, String to, Set<String> requiredPermissions, Set<String> requiredFields) {}
}
