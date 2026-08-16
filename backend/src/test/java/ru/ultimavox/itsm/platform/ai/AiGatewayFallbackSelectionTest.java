package ru.ultimavox.itsm.platform.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.mock.env.MockEnvironment;

/** Verifies Ollama vs logging gateway activation without a live model server. */
class AiGatewayFallbackSelectionTest {

  @Test
  void ollama_enabled_when_url_present() {
    MockEnvironment env = new MockEnvironment().withProperty("itsm.ai.ollama.url", "http://localhost:11434/v1");
    var ctx = new SimpleConditionContext(env);

    assertThat(new OllamaEnabledCondition().matches(ctx, new StandardAnnotationMetadata(OllamaAiGateway.class)))
        .isTrue();
    assertThat(new OllamaDisabledCondition().matches(ctx, new StandardAnnotationMetadata(LoggingAiGateway.class)))
        .isFalse();
  }

  @Test
  void logging_active_when_url_blank_or_missing() {
    MockEnvironment blank = new MockEnvironment().withProperty("itsm.ai.ollama.url", "");
    MockEnvironment missing = new MockEnvironment();
    var enabled = new OllamaEnabledCondition();
    var disabled = new OllamaDisabledCondition();

    assertThat(enabled.matches(new SimpleConditionContext(blank), null)).isFalse();
    assertThat(disabled.matches(new SimpleConditionContext(blank), null)).isTrue();
    assertThat(enabled.matches(new SimpleConditionContext(missing), null)).isFalse();
    assertThat(disabled.matches(new SimpleConditionContext(missing), null)).isTrue();
  }

  /** Minimal ConditionContext for unit-testing Spring {@code Condition}s. */
  private static final class SimpleConditionContext
      implements org.springframework.context.annotation.ConditionContext {

    private final org.springframework.core.env.Environment environment;

    SimpleConditionContext(org.springframework.core.env.Environment environment) {
      this.environment = environment;
    }

    @Override
    public org.springframework.beans.factory.config.ConfigurableListableBeanFactory getBeanFactory() {
      return null;
    }

    @Override
    public org.springframework.beans.factory.support.BeanDefinitionRegistry getRegistry() {
      return null;
    }

    @Override
    public org.springframework.core.env.Environment getEnvironment() {
      return environment;
    }

    @Override
    public org.springframework.core.io.ResourceLoader getResourceLoader() {
      return null;
    }

    @Override
    public ClassLoader getClassLoader() {
      return getClass().getClassLoader();
    }
  }
}
