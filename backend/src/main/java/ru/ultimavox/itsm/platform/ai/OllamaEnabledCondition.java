package ru.ultimavox.itsm.platform.ai;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/** Matches when {@code itsm.ai.ollama.url} is a non-blank base URL. */
class OllamaEnabledCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    String url = context.getEnvironment().getProperty("itsm.ai.ollama.url", "");
    return StringUtils.hasText(url);
  }
}
