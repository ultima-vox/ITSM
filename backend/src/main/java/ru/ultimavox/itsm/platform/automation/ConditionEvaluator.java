package ru.ultimavox.itsm.platform.automation;

import org.springframework.stereotype.Component;
import ru.ultimavox.itsm.platform.automation.AutomationRule.Condition;
import ru.ultimavox.itsm.platform.automation.AutomationRule.Operator;
import ru.ultimavox.itsm.platform.event.DomainEvent;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Evaluates automation conditions against domain event payload data. */
@Component
public class ConditionEvaluator {

    boolean matches(DomainEvent event, java.util.List<Condition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        Map<String, Object> data = event.data() == null ? Map.of() : event.data();
        for (Condition condition : conditions) {
            if (!matchesOne(data.get(condition.field()), condition)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesOne(Object fieldValue, Condition condition) {
        String actual = fieldValue == null ? null : String.valueOf(fieldValue);
        String expected = condition.value();
        Operator operator = condition.operator();

        return switch (operator) {
            case EQUALS -> actual != null && actual.equals(expected);
            case NOT_EQUALS -> actual == null || !actual.equals(expected);
            case CONTAINS -> actual != null && actual.contains(expected);
            case IN -> {
                Set<String> options = Arrays.stream(expected.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet());
                yield actual != null && options.contains(actual);
            }
            case GREATER_THAN -> {
                if (actual == null) {
                    yield false;
                }
                try {
                    yield Double.parseDouble(actual) > Double.parseDouble(expected);
                } catch (NumberFormatException ex) {
                    yield actual.compareTo(expected) > 0;
                }
            }
        };
    }
}
