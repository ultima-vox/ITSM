package ru.ultimavox.itsm.servicecatalog.application;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Small deterministic policy grammar; never executes script or SpEL. Unknown syntax denies. */
@Component
public class CatalogEligibilityEvaluator {
  private static final Pattern RULE = Pattern.compile(
      "^(subject|form)\\.([A-Za-z][A-Za-z0-9_-]{0,63})\\s*(==|!=)\\s*'([^']{0,240})'$"
  );

  public boolean matches(String expression, Map<String, Object> subject, Map<String, Object> form) {
    if (expression == null || expression.isBlank()) return false;
    Matcher match = RULE.matcher(expression.trim());
    if (!match.matches()) return false;
    Object actual = ("subject".equals(match.group(1)) ? subject : form).get(match.group(2));
    String expected = match.group(4);
    boolean equal = actual instanceof Collection<?> values
        ? values.stream().map(String::valueOf).anyMatch(expected::equals)
        : actual != null && expected.equals(String.valueOf(actual));
    return "==".equals(match.group(3)) ? equal : !equal;
  }
}
