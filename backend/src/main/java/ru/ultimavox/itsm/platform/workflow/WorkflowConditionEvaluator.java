package ru.ultimavox.itsm.platform.workflow;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import ru.ultimavox.itsm.platform.workflow.WorkflowDefinition.Condition;
import ru.ultimavox.itsm.platform.workflow.WorkflowDefinition.ConditionOperator;

/** Deterministic allowlisted workflow condition DSL. No reflection, expressions, or executable code. */
final class WorkflowConditionEvaluator {
  void requireMatches(List<Condition> conditions, Map<String, Object> fields, String transitionKey) {
    for (Condition condition : conditions) {
      if (!matches(condition, fields)) {
        throw new WorkflowTransitionException(
            "Condition '%s %s' is not satisfied for transition '%s'"
                .formatted(condition.field(), condition.operator(), transitionKey));
      }
    }
  }

  boolean matches(Condition condition, Map<String, Object> fields) {
    Object actual = resolve(fields, condition.field());
    Object expected = condition.value();
    return switch (condition.operator()) {
      case EXISTS -> ((Boolean) expected) == (actual != null);
      case EQUALS -> actual != null && scalarEquals(actual, expected);
      case NOT_EQUALS -> actual != null && !scalarEquals(actual, expected);
      case IN -> actual != null && expected instanceof Collection<?> values
          && values.stream().anyMatch(value -> scalarEquals(actual, value));
      case CONTAINS -> contains(actual, expected);
      case GT -> compared(actual, expected, value -> value > 0);
      case GTE -> compared(actual, expected, value -> value >= 0);
      case LT -> compared(actual, expected, value -> value < 0);
      case LTE -> compared(actual, expected, value -> value <= 0);
    };
  }

  private Object resolve(Map<String, Object> fields, String path) {
    Object current = fields;
    for (String segment : path.split("\\.")) {
      if (!(current instanceof Map<?, ?> map) || !map.containsKey(segment)) return null;
      current = map.get(segment);
    }
    return current;
  }

  private boolean contains(Object actual, Object expected) {
    if (actual instanceof String text && expected instanceof String token) return text.contains(token);
    if (actual instanceof Collection<?> values) {
      return values.stream().anyMatch(value -> scalarEquals(value, expected));
    }
    return false;
  }

  private boolean scalarEquals(Object left, Object right) {
    BigDecimal leftNumber = number(left);
    BigDecimal rightNumber = number(right);
    if (leftNumber != null && rightNumber != null) return leftNumber.compareTo(rightNumber) == 0;
    return left != null && right != null && left.getClass().equals(right.getClass()) && left.equals(right);
  }

  private boolean compared(Object left, Object right, java.util.function.IntPredicate predicate) {
    BigDecimal leftNumber = number(left);
    BigDecimal rightNumber = number(right);
    return leftNumber != null && rightNumber != null && predicate.test(leftNumber.compareTo(rightNumber));
  }

  private BigDecimal number(Object value) {
    if (!(value instanceof Number number)) return null;
    try { return new BigDecimal(number.toString()); }
    catch (NumberFormatException ignored) { return null; }
  }
}
