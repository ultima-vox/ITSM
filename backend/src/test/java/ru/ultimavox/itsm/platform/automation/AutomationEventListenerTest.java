package ru.ultimavox.itsm.platform.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.ultimavox.itsm.platform.event.AutomationDepthContext;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.event.DomainEventEnvelope;

class AutomationEventListenerTest {

  private final DomainEvent event = new DomainEvent(
      UUID.randomUUID(), "work-item.created", 1, Instant.now(), UUID.randomUUID(),
      "work_item", UUID.randomUUID().toString(), Map.of());

  @Test
  void runsMatchingRulesForUserOriginatedEvent() {
    AutomationRunner runner = mock(AutomationRunner.class);
    when(runner.handle(event)).thenReturn(2);
    AutomationEventListener listener = new AutomationEventListener(runner);

    listener.onCommittedDomainEvent(new DomainEventEnvelope(event, 0));

    verify(runner).handle(event);
    assertThat(AutomationDepthContext.current()).isZero();
  }

  @Test
  void ignoresEventsProducedBeyondMaxAutomationDepth() {
    AutomationRunner runner = mock(AutomationRunner.class);
    AutomationEventListener listener = new AutomationEventListener(runner);
    int max = AutomationDepthContext.MAX_AUTOMATION_DEPTH;

    listener.onCommittedDomainEvent(new DomainEventEnvelope(event, max));

    verify(runner, never()).handle(any());
  }

  @Test
  void containsRunnerFailuresSoCommittedMutationIsNeverAffected() {
    AutomationRunner runner = mock(AutomationRunner.class);
    doThrow(new IllegalStateException("db unavailable")).when(runner).handle(any());
    AutomationEventListener listener = new AutomationEventListener(runner);

    listener.onCommittedDomainEvent(new DomainEventEnvelope(event, 0));

    verify(runner).handle(event);
    assertThat(AutomationDepthContext.current()).isZero();
  }
}
