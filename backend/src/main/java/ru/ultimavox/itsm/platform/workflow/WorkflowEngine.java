package ru.ultimavox.itsm.platform.workflow;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.PermissionChecker;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.platform.workflow.WorkflowDefinition.Transition;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Authoritative workflow transition engine. Checks permissions and required fields,
 * persists instance state, audits and emits domain events in one transaction.
 */
@Service
public class WorkflowEngine {

    private final WorkflowDefinitionRepository definitions;
    private final WorkflowInstanceRepository instances;
    private final PermissionChecker permissions;
    private final AuditTrail audit;
    private final IntegrationEventOutbox outbox;

    public WorkflowEngine(
            WorkflowDefinitionRepository definitions,
            WorkflowInstanceRepository instances,
            PermissionChecker permissions,
            AuditTrail audit,
            IntegrationEventOutbox outbox
    ) {
        this.definitions = definitions;
        this.instances = instances;
        this.permissions = permissions;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowDefinition> loadDefinition(String objectKey) {
        return definitions.findActiveByObjectKey(objectKey);
    }

    /**
     * Pure guard: whether the named transition is legal for the current state and payload.
     * Permission checks use the provided subject against requiredPermissions.
     */
    public boolean canTransition(
            WorkflowDefinition definition,
            String currentState,
            String transitionKey,
            String subject,
            String objectType,
            String objectId,
            Map<String, Object> fields
    ) {
        try {
            resolveAndGuard(definition, currentState, transitionKey, subject, objectType, objectId, fields);
            return true;
        } catch (WorkflowTransitionException ex) {
            return false;
        }
    }

    /**
     * Applies a transition: creates or updates {@link WorkflowInstance}, audits and outboxes.
     *
     * @return the new state after transition
     */
    @Transactional
    public WorkflowInstance applyTransition(TransitionCommand command) {
        Objects.requireNonNull(command, "command");
        WorkflowDefinition definition = definitions.findActiveByObjectKey(command.objectType())
                .orElseThrow(() -> new WorkflowTransitionException(
                        "No active workflow for object type: " + command.objectType()));

        WorkflowInstance current = instances.findByObject(command.objectType(), command.objectId())
                .orElseGet(() -> startInstance(definition, command.objectType(), command.objectId()));

        Transition transition = resolveAndGuard(
                definition,
                current.state(),
                command.transitionKey(),
                command.subject(),
                command.objectType(),
                command.objectId(),
                command.fields()
        );

        String fromState = current.state();
        WorkflowInstance updated = instances.updateState(current, transition.to(), current.version());

        Instant now = Instant.now();
        UUID correlationId = command.correlationId() != null ? command.correlationId() : UUID.randomUUID();

        Map<String, Object> before = Map.of("state", fromState);
        Map<String, Object> after = Map.of(
                "state", updated.state(),
                "transition", transition.key(),
                "definitionVersion", definition.version()
        );

        audit.append(new AuditTrail.Entry(
                command.subject(),
                "workflow.transitioned",
                command.objectType(),
                command.objectId(),
                before,
                after,
                correlationId,
                now
        ));

        Map<String, Object> eventData = new HashMap<>(after);
        eventData.put("fromState", fromState);
        eventData.put("toState", updated.state());
        eventData.put("transitionKey", transition.key());
        if (command.fields() != null) {
            eventData.put("fields", command.fields());
        }

        outbox.record(new DomainEvent(
                UUID.randomUUID(),
                command.objectType() + ".transitioned",
                1,
                now,
                correlationId,
                command.objectType(),
                command.objectId(),
                Map.copyOf(eventData)
        ));

        return updated;
    }

    /**
     * Ensures a workflow instance exists in the definition's initial state.
     */
    @Transactional
    public WorkflowInstance ensureStarted(String objectType, String objectId) {
        return instances.findByObject(objectType, objectId)
                .orElseGet(() -> {
                    WorkflowDefinition definition = definitions.findActiveByObjectKey(objectType)
                            .orElseThrow(() -> new WorkflowTransitionException(
                                    "No active workflow for object type: " + objectType));
                    return startInstance(definition, objectType, objectId);
                });
    }

    private WorkflowInstance startInstance(WorkflowDefinition definition, String objectType, String objectId) {
        WorkflowInstance created = new WorkflowInstance(
                UUID.randomUUID(),
                objectType,
                objectId,
                definition.initialState(),
                definition.version(),
                1,
                Instant.now()
        );
        return instances.insert(created);
    }

    private Transition resolveAndGuard(
            WorkflowDefinition definition,
            String currentState,
            String transitionKey,
            String subject,
            String objectType,
            String objectId,
            Map<String, Object> fields
    ) {
        Transition transition = definition.findTransition(transitionKey)
                .orElseThrow(() -> new WorkflowTransitionException(
                        "Unknown transition '%s' for workflow '%s'".formatted(transitionKey, definition.objectKey())));

        if (!transition.from().equals(currentState)) {
            throw new WorkflowTransitionException(
                    "Transition '%s' is not allowed from state '%s' (requires '%s')"
                            .formatted(transitionKey, currentState, transition.from()));
        }

        if (!definition.states().contains(transition.to())) {
            throw new WorkflowTransitionException(
                    "Transition '%s' targets unknown state '%s'".formatted(transitionKey, transition.to()));
        }

        for (String permission : transition.requiredPermissions()) {
            PermissionChecker.Decision decision = permissions.check(
                    new PermissionChecker.Request(subject, permission, objectType, objectId, null)
            );
            if (!decision.allowed()) {
                throw new WorkflowTransitionException(
                        "Subject lacks permission '%s' for transition '%s'".formatted(permission, transitionKey));
            }
        }

        Map<String, Object> payload = fields == null ? Map.of() : fields;
        for (String requiredField : transition.requiredFields()) {
            Object value = payload.get(requiredField);
            boolean missing = value == null || (value instanceof String s && s.isBlank());
            if (missing) {
                throw new WorkflowTransitionException(
                        "Required field '%s' is missing for transition '%s'".formatted(requiredField, transitionKey));
            }
        }

        return transition;
    }

    public record TransitionCommand(
            String subject,
            String objectType,
            String objectId,
            String transitionKey,
            Map<String, Object> fields,
            UUID correlationId
    ) {
        public TransitionCommand(String subject, String objectType, String objectId, String transitionKey, Map<String, Object> fields) {
            this(subject, objectType, objectId, transitionKey, fields, null);
        }
    }
}
