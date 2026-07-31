package ru.ultimavox.itsm.platform.search;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/** Matches when {@code itsm.opensearch.url} is a non-blank base URL. */
class OpenSearchEnabledCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    String url = context.getEnvironment().getProperty("itsm.opensearch.url", "");
    return StringUtils.hasText(url);
  }
}
