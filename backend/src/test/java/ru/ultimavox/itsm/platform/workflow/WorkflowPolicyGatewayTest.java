package ru.ultimavox.itsm.platform.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import ru.ultimavox.itsm.platform.workflow.WorkflowDefinition.Transition;
import ru.ultimavox.itsm.platform.workflow.WorkflowEngine.TransitionCommand;

class WorkflowPolicyGatewayTest {
  private final WorkflowEngine engine = Mockito.mock(WorkflowEngine.class);
  private final WorkflowPolicyGateway gateway = new WorkflowPolicyGateway(engine);

  @Test
  void missingDefinitionLeavesAggregateAsAuthority() {
    when(engine.loadDefinition("problem")).thenReturn(Optional.empty());

    assertThat(gateway.enforceByTarget(
        "actor", "problem", "42", "NEW", "UNDER_INVESTIGATION", Map.of(), UUID.randomUUID()
    )).isFalse();
    verify(engine, never()).applyTransition(any());
  }

  @Test
  void configuredWorkflowFailsClosedWhenEdgeIsMissing() {
    when(engine.loadDefinition("problem")).thenReturn(Optional.of(definition()));

    assertThatThrownBy(() -> gateway.enforceByTarget(
        "actor", "problem", "42", "NEW", "CLOSED", Map.of(), UUID.randomUUID()
    )).isInstanceOf(WorkflowTransitionException.class)
        .hasMessageContaining("has no transition NEW -> CLOSED");
    verify(engine, never()).applyTransition(any());
  }

  @Test
  void configuredWorkflowUsesMatchingVersionedTransition() {
    when(engine.loadDefinition("problem")).thenReturn(Optional.of(definition()));
    UUID correlationId = UUID.randomUUID();

    assertThat(gateway.enforceByTarget(
        "actor", "problem", "42", "NEW", "UNDER_INVESTIGATION",
        Map.of("root_cause", "network"), correlationId
    )).isTrue();

    ArgumentCaptor<TransitionCommand> command = ArgumentCaptor.forClass(TransitionCommand.class);
    verify(engine).applyTransition(command.capture());
    assertThat(command.getValue().transitionKey()).isEqualTo("investigate");
    assertThat(command.getValue().correlationId()).isEqualTo(correlationId);
  }

  private WorkflowDefinition definition() {
    return new WorkflowDefinition(
        UUID.randomUUID(), "problem", 1, "NEW",
        Set.of("NEW", "UNDER_INVESTIGATION", "CLOSED"),
        List.of(new Transition(
            "investigate", "NEW", "UNDER_INVESTIGATION", Set.of("problem.write"), Set.of()
        ))
    );
  }
}
