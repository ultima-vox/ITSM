package ru.ultimavox.itsm.platform.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.mock.env.MockEnvironment;

/**
 * Verifies JDBC vs OpenSearch activation without a live OpenSearch or database.
 */
class SearchIndexFallbackSelectionTest {

  @Test
  void open_search_enabled_when_url_present() {
    MockEnvironment env = new MockEnvironment().withProperty("itsm.opensearch.url", "http://localhost:9200");
    OpenSearchEnabledCondition enabled = new OpenSearchEnabledCondition();
    OpenSearchDisabledCondition disabled = new OpenSearchDisabledCondition();

    var ctx = new SimpleConditionContext(env);
    assertThat(enabled.matches(ctx, new StandardAnnotationMetadata(OpenSearchSearchIndexService.class))).isTrue();
    assertThat(disabled.matches(ctx, new StandardAnnotationMetadata(JdbcSearchIndexService.class))).isFalse();
  }

  @Test
  void jdbc_active_when_url_blank_or_missing() {
    MockEnvironment blank = new MockEnvironment().withProperty("itsm.opensearch.url", "");
    MockEnvironment missing = new MockEnvironment();
    OpenSearchEnabledCondition enabled = new OpenSearchEnabledCondition();
    OpenSearchDisabledCondition disabled = new OpenSearchDisabledCondition();

    assertThat(enabled.matches(new SimpleConditionContext(blank), null)).isFalse();
    assertThat(disabled.matches(new SimpleConditionContext(blank), null)).isTrue();
    assertThat(enabled.matches(new SimpleConditionContext(missing), null)).isFalse();
    assertThat(disabled.matches(new SimpleConditionContext(missing), null)).isTrue();
  }

  @Test
  void noop_search_returns_empty_without_infra() {
    NoOpSearchIndexService noop = new NoOpSearchIndexService();
    assertThat(noop.search("anything", java.util.Set.of("x"), 10)).isEmpty();
  }

  @Test
  void environment_without_opensearch_url_uses_jdbc_path_in_standard_env() {
    StandardEnvironment env = new StandardEnvironment();
    env.getPropertySources().addFirst(new MapPropertySource("test", java.util.Map.of()));
    assertThat(env.getProperty("itsm.opensearch.url", "")).isBlank();
    assertThat(new OpenSearchDisabledCondition().matches(new SimpleConditionContext(env), null)).isTrue();
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
