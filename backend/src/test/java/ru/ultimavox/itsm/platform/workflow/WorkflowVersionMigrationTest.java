package ru.ultimavox.itsm.platform.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.PermissionChecker;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

class WorkflowVersionMigrationTest {
  private final WorkflowDefinitionRepository definitions = mock(WorkflowDefinitionRepository.class);
  private final WorkflowInstanceRepository instances = mock(WorkflowInstanceRepository.class);
  private final AuditTrail audit = mock(AuditTrail.class);
  private final IntegrationEventOutbox outbox = mock(IntegrationEventOutbox.class);
  private final PermissionChecker permissions = request -> PermissionChecker.Decision.allow("test");
  private final WorkflowEngine engine = new WorkflowEngine(definitions, instances, permissions, audit, outbox);

  @Test
  void existingInstanceUsesPinnedDefinitionInsteadOfNewActiveVersion() {
    WorkflowInstance current = instance("NEW", 1, 4);
    WorkflowDefinition pinned = definition(1, Set.of("NEW", "IN_PROGRESS"),
        List.of(new WorkflowDefinition.Transition("start", "NEW", "IN_PROGRESS", Set.of(), Set.of())));
    when(instances.findByObject("work-item", "42")).thenReturn(Optional.of(current));
    when(definitions.findByObjectKeyAndVersion("work-item", 1)).thenReturn(Optional.of(pinned));
    when(instances.updateState(current, "IN_PROGRESS", 4))
        .thenReturn(new WorkflowInstance(current.id(), "work-item", "42", "IN_PROGRESS", 1, 5, Instant.now()));

    WorkflowInstance result = engine.applyTransition(new WorkflowEngine.TransitionCommand(
        "agent", "work-item", "42", "start", Map.of()));

    assertThat(result.definitionVersion()).isEqualTo(1);
    verify(definitions, never()).findActiveByObjectKey("work-item");
    verify(definitions).findByObjectKeyAndVersion("work-item", 1);
  }

  @Test
  void migratesCompatibleStateWithOptimisticVersionAndEvidence() {
    WorkflowInstance current = instance("IN_PROGRESS", 1, 7);
    WorkflowDefinition target = definition(2, Set.of("NEW", "IN_PROGRESS", "DONE"), List.of());
    WorkflowInstance migrated = new WorkflowInstance(current.id(), "work-item", "42", "IN_PROGRESS", 2, 8, Instant.now());
    when(instances.findByObject("work-item", "42")).thenReturn(Optional.of(current));
    when(definitions.findByObjectKeyAndVersion("work-item", 2)).thenReturn(Optional.of(target));
    when(instances.updateDefinitionVersion(current, 2, 7)).thenReturn(migrated);

    WorkflowInstance result = engine.migrateInstance(new WorkflowEngine.MigrationCommand(
        "admin", "work-item", "42", 2, 7, UUID.randomUUID()));

    assertThat(result.definitionVersion()).isEqualTo(2);
    assertThat(result.version()).isEqualTo(8);
    verify(audit).append(org.mockito.ArgumentMatchers.any());
    verify(outbox).record(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void rejectsMigrationWhenStateMissingOrExpectedVersionStale() {
    WorkflowInstance current = instance("IN_PROGRESS", 1, 7);
    when(instances.findByObject("work-item", "42")).thenReturn(Optional.of(current));
    when(definitions.findByObjectKeyAndVersion("work-item", 2))
        .thenReturn(Optional.of(definition(2, Set.of("NEW", "DONE"), List.of())));

    assertThatThrownBy(() -> engine.migrateInstance(new WorkflowEngine.MigrationCommand(
        "admin", "work-item", "42", 2, 6, null)))
        .isInstanceOf(WorkflowTransitionException.class).hasMessageContaining("version conflict");
    assertThatThrownBy(() -> engine.migrateInstance(new WorkflowEngine.MigrationCommand(
        "admin", "work-item", "42", 2, 7, null)))
        .isInstanceOf(WorkflowTransitionException.class).hasMessageContaining("does not contain active state");
    verify(instances, never()).updateDefinitionVersion(current, 2, 7);
  }

  private static WorkflowInstance instance(String state, int definitionVersion, int version) {
    return new WorkflowInstance(UUID.randomUUID(), "work-item", "42", state,
        definitionVersion, version, Instant.now());
  }

  private static WorkflowDefinition definition(
      int version, Set<String> states, List<WorkflowDefinition.Transition> transitions) {
    return new WorkflowDefinition(UUID.randomUUID(), "work-item", version, "NEW", states, transitions);
  }
}
