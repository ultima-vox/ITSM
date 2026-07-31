package ru.ultimavox.itsm.platform.automation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import ru.ultimavox.itsm.platform.automation.AutomationRule.Condition;
import ru.ultimavox.itsm.platform.automation.AutomationRule.Operator;
import ru.ultimavox.itsm.platform.event.DomainEvent;

class ConditionEvaluatorTest {

    private final ConditionEvaluator evaluator = new ConditionEvaluator();

    @Test
    void empty_conditions_match() {
        DomainEvent event = sample(Map.of("priority", "HIGH"));
        assertThat(evaluator.matches(event, List.of())).isTrue();
    }

    @Test
    void equals_and_in_operators() {
        DomainEvent event = sample(Map.of("priority", "CRITICAL", "service", "Workplace"));
        assertThat(evaluator.matches(event, List.of(
                new Condition("priority", Operator.EQUALS, "CRITICAL"),
                new Condition("service", Operator.IN, "Workplace,Network")
        ))).isTrue();
        assertThat(evaluator.matches(event, List.of(
                new Condition("priority", Operator.EQUALS, "LOW")
        ))).isFalse();
    }

    @Test
    void greater_than_numeric() {
        DomainEvent event = sample(Map.of("impact", "3"));
        assertThat(evaluator.matches(event, List.of(
                new Condition("impact", Operator.GREATER_THAN, "2")
        ))).isTrue();
    }

    private static DomainEvent sample(Map<String, Object> data) {
        return new DomainEvent(
                UUID.randomUUID(),
                "incident.created",
                1,
                Instant.now(),
                UUID.randomUUID(),
                "work-item",
                UUID.randomUUID().toString(),
                data
        );
    }
}
