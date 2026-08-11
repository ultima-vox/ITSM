package ru.ultimavox.itsm.platform.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.ultimavox.itsm.platform.workflow.WorkflowDefinition.Condition;
import ru.ultimavox.itsm.platform.workflow.WorkflowDefinition.ConditionOperator;

class WorkflowConditionEvaluatorTest {
  private final WorkflowConditionEvaluator evaluator = new WorkflowConditionEvaluator();

  @Test
  void evaluatesNestedTypedAndCollectionConditions() {
    Map<String, Object> fields = Map.of(
        "risk", 7,
        "requester", Map.of("country", "RU"),
        "tags", List.of("vip", "production"),
        "summary", "Database unavailable");

    assertThat(evaluator.matches(condition("risk", "GTE", 7.0), fields)).isTrue();
    assertThat(evaluator.matches(condition("requester.country", "EQUALS", "RU"), fields)).isTrue();
    assertThat(evaluator.matches(condition("tags", "CONTAINS", "vip"), fields)).isTrue();
    assertThat(evaluator.matches(condition("summary", "CONTAINS", "unavailable"), fields)).isTrue();
    assertThat(evaluator.matches(condition("requester.city", "EXISTS", false), fields)).isTrue();
    assertThat(evaluator.matches(condition("risk", "IN", List.of(5, 7, 9)), fields)).isTrue();
  }

  @Test
  void missingOrWrongTypesFailClosed() {
    Map<String, Object> fields = Map.of("risk", "high", "flag", true);
    assertThat(evaluator.matches(condition("missing", "NOT_EQUALS", "x"), fields)).isFalse();
    assertThat(evaluator.matches(condition("risk", "LT", 10), fields)).isFalse();
    assertThat(evaluator.matches(condition("flag", "EQUALS", "true"), fields)).isFalse();
    assertThatThrownBy(() -> evaluator.requireMatches(
        List.of(condition("risk", "GT", 5)), fields, "approve"))
        .isInstanceOf(WorkflowTransitionException.class).hasMessageContaining("risk GT");
  }

  @Test
  void definitionRejectsUnsafePathsAndMalformedExists() {
    assertThatThrownBy(() -> new Condition("a..b", ConditionOperator.EQUALS, "x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Condition("risk", ConditionOperator.EXISTS, "true"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private Condition condition(String field, String operator, Object value) {
    return new Condition(field, ConditionOperator.valueOf(operator), value);
  }
}
