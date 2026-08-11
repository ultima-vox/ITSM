package ru.ultimavox.itsm.platform.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.ultimavox.itsm.platform.event.DomainEvent;

class AutomationRunnerStatusTest {
    @Test
    void recordsSuccessTerminalStatus() {
        Fixture fixture = fixture();
        assertThat(fixture.runner.handle(fixture.event)).isEqualTo(1);
        verify(fixture.log).complete(eq("rule"), eq(fixture.event.id()), eq("log"), eq("SUCCEEDED"), any());
    }

    @Test
    void recordsFailureTerminalStatusWithoutBreakingEventDispatch() {
        Fixture fixture = fixture();
        doThrow(new IllegalStateException("downstream unavailable")).when(fixture.executor)
                .execute(any(), eq(fixture.event));
        assertThat(fixture.runner.handle(fixture.event)).isZero();
        verify(fixture.log).complete(eq("rule"), eq(fixture.event.id()), eq("log"), eq("FAILED"), any());
    }

    private static Fixture fixture() {
        AutomationRuleRepository rules = mock(AutomationRuleRepository.class);
        ConditionEvaluator conditions = mock(ConditionEvaluator.class);
        AllowlistedActionExecutor executor = mock(AllowlistedActionExecutor.class);
        AutomationActionLogRepository log = mock(AutomationActionLogRepository.class);
        DomainEvent event = new DomainEvent(UUID.randomUUID(), "work-item.created", 1, Instant.now(),
                UUID.randomUUID(), "work_item", UUID.randomUUID().toString(), Map.of());
        AutomationRule rule = new AutomationRule(UUID.randomUUID(), "rule", "Rule", true,
                new AutomationRule.Trigger("work-item.created"), List.of(),
                List.of(new AutomationRule.Action("log", Map.of())));
        when(rules.findEnabledByEventType(event.type())).thenReturn(List.of(rule));
        when(conditions.matches(event, rule.conditions())).thenReturn(true);
        when(log.tryLog(eq("rule"), eq(event.id()), eq("log"), eq("STARTED"), any())).thenReturn(true);
        return new Fixture(new AutomationRunner(rules, conditions, executor, log), executor, log, event);
    }

    private record Fixture(AutomationRunner runner, AllowlistedActionExecutor executor,
                           AutomationActionLogRepository log, DomainEvent event) {}
}
