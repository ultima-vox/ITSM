package ru.ultimavox.itsm.platform.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.PermissionChecker;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.platform.workflow.WorkflowDefinition.Transition;
import ru.ultimavox.itsm.platform.workflow.WorkflowEngine.TransitionCommand;

@ExtendWith(MockitoExtension.class)
class WorkflowEngineTest {

    @Mock AuditTrail audit;
    @Mock IntegrationEventOutbox outbox;

    private InMemoryDefinitionRepository definitions;
    private InMemoryInstanceRepository instances;
    private StubPermissionChecker permissions;
    private WorkflowEngine engine;
    private WorkflowDefinition workItemWorkflow;

    @BeforeEach
    void setUp() {
        definitions = new InMemoryDefinitionRepository();
        instances = new InMemoryInstanceRepository();
        permissions = new StubPermissionChecker();
        engine = new WorkflowEngine(definitions, instances, permissions, audit, outbox);

        workItemWorkflow = new WorkflowDefinition(
                UUID.randomUUID(),
                "work-item",
                1,
                "NEW",
                Set.of("NEW", "IN_PROGRESS", "RESOLVED", "CLOSED", "CANCELLED"),
                List.of(
                        new Transition("start", "NEW", "IN_PROGRESS",
                                Set.of("work-item.transition"), Set.of("assignee_id")),
                        new Transition("resolve", "IN_PROGRESS", "RESOLVED",
                                Set.of("work-item.transition"), Set.of()),
                        new Transition("close", "RESOLVED", "CLOSED",
                                Set.of("work-item.close"), Set.of())
                )
        );
        definitions.put(workItemWorkflow);
    }

    @Test
    void canTransition_allows_legal_start_with_permission_and_required_field() {
        permissions.allow("agent-1", "work-item.transition");

        boolean allowed = engine.canTransition(
                workItemWorkflow,
                "NEW",
                "start",
                "agent-1",
                "work-item",
                "obj-1",
                Map.of("assignee_id", "agent-1")
        );

        assertThat(allowed).isTrue();
    }

    @Test
    void canTransition_rejects_missing_required_field() {
        permissions.allow("agent-1", "work-item.transition");

        boolean allowed = engine.canTransition(
                workItemWorkflow,
                "NEW",
                "start",
                "agent-1",
                "work-item",
                "obj-1",
                Map.of()
        );

        assertThat(allowed).isFalse();
    }

    @Test
    void canTransition_rejects_missing_permission() {
        boolean allowed = engine.canTransition(
                workItemWorkflow,
                "NEW",
                "start",
                "requester-1",
                "work-item",
                "obj-1",
                Map.of("assignee_id", "agent-1")
        );

        assertThat(allowed).isFalse();
    }

    @Test
    void canTransition_rejects_wrong_source_state() {
        permissions.allow("agent-1", "work-item.transition");

        boolean allowed = engine.canTransition(
                workItemWorkflow,
                "RESOLVED",
                "start",
                "agent-1",
                "work-item",
                "obj-1",
                Map.of("assignee_id", "agent-1")
        );

        assertThat(allowed).isFalse();
    }

    @Test
    void applyTransition_persists_state_audits_and_emits_event() {
        permissions.allow("agent-1", "work-item.transition");
        UUID objectId = UUID.randomUUID();

        WorkflowInstance result = engine.applyTransition(new TransitionCommand(
                "agent-1",
                "work-item",
                objectId.toString(),
                "start",
                Map.of("assignee_id", "agent-1")
        ));

        assertThat(result.state()).isEqualTo("IN_PROGRESS");
        assertThat(instances.findByObject("work-item", objectId.toString())).isPresent();
        assertThat(instances.findByObject("work-item", objectId.toString()).orElseThrow().state())
                .isEqualTo("IN_PROGRESS");

        ArgumentCaptor<AuditTrail.Entry> auditCaptor = ArgumentCaptor.forClass(AuditTrail.Entry.class);
        verify(audit).append(auditCaptor.capture());
        assertThat(auditCaptor.getValue().action()).isEqualTo("workflow.transitioned");
        assertThat(auditCaptor.getValue().after()).containsEntry("state", "IN_PROGRESS");

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox).record(eventCaptor.capture());
        assertThat(eventCaptor.getValue().type()).isEqualTo("workflow.transitioned");
        assertThat(eventCaptor.getValue().data()).containsEntry("fromState", "NEW");
        assertThat(eventCaptor.getValue().data()).containsEntry("toState", "IN_PROGRESS");
    }

    @Test
    void applyTransition_fails_without_permission_and_does_not_emit() {
        assertThatThrownBy(() -> engine.applyTransition(new TransitionCommand(
                "nobody",
                "work-item",
                "obj-9",
                "start",
                Map.of("assignee_id", "x")
        ))).isInstanceOf(WorkflowTransitionException.class)
                .hasMessageContaining("permission");

        verify(outbox, never()).record(any());
        verify(audit, never()).append(any());
    }

    @Test
    void applyTransition_fails_on_unknown_transition() {
        permissions.allow("agent-1", "work-item.transition");

        assertThatThrownBy(() -> engine.applyTransition(new TransitionCommand(
                "agent-1",
                "work-item",
                "obj-2",
                "teleport",
                Map.of()
        ))).isInstanceOf(WorkflowTransitionException.class)
                .hasMessageContaining("Unknown transition");
    }

    // --- test doubles ---

    static final class StubPermissionChecker implements PermissionChecker {
        private final Set<String> grants = ConcurrentHashMap.newKeySet();

        void allow(String subject, String permission) {
            grants.add(subject + "|" + permission);
        }

        @Override
        public Decision check(Request request) {
            boolean ok = grants.contains(request.subject() + "|" + request.permission());
            return ok ? Decision.allow("test") : Decision.deny("test-deny");
        }
    }

    static final class InMemoryDefinitionRepository implements WorkflowDefinitionRepository {
        private final Map<String, WorkflowDefinition> byKey = new ConcurrentHashMap<>();

        void put(WorkflowDefinition definition) {
            byKey.put(definition.objectKey(), definition);
        }

        @Override
        public Optional<WorkflowDefinition> findActiveByObjectKey(String objectKey) {
            return Optional.ofNullable(byKey.get(objectKey));
        }

        @Override
        public List<WorkflowDefinitionView> listAll() {
            return byKey.values().stream()
                .map(d -> new WorkflowDefinitionView(d, true))
                .toList();
        }

        @Override
        public Optional<WorkflowDefinitionView> setActive(UUID id, boolean active) {
            return byKey.values().stream()
                .filter(d -> d.id().equals(id))
                .findFirst()
                .map(d -> new WorkflowDefinitionView(d, active));
        }
    }

    static final class InMemoryInstanceRepository implements WorkflowInstanceRepository {
        private final Map<String, WorkflowInstance> store = new ConcurrentHashMap<>();

        private static String key(String type, String id) {
            return type + ":" + id;
        }

        @Override
        public Optional<WorkflowInstance> findByObject(String objectType, String objectId) {
            return Optional.ofNullable(store.get(key(objectType, objectId)));
        }

        @Override
        public WorkflowInstance insert(WorkflowInstance instance) {
            store.put(key(instance.objectType(), instance.objectId()), instance);
            return instance;
        }

        @Override
        public WorkflowInstance updateState(WorkflowInstance instance, String newState, int expectedVersion) {
            WorkflowInstance current = store.get(key(instance.objectType(), instance.objectId()));
            if (current == null || current.version() != expectedVersion) {
                throw new IllegalStateException("optimistic lock");
            }
            WorkflowInstance updated = new WorkflowInstance(
                    current.id(),
                    current.objectType(),
                    current.objectId(),
                    newState,
                    current.definitionVersion(),
                    expectedVersion + 1,
                    Instant.now()
            );
            store.put(key(current.objectType(), current.objectId()), updated);
            return updated;
        }
    }
}
