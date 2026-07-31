package ru.ultimavox.itsm.platform.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.type.StandardAnnotationMetadata;

/**
 * Documents / verifies selection rules without a live Redis or full Spring Boot context.
 * Bean methods in {@link CacheConfiguration} are gated by property + missing-bean conditions.
 */
class CachePortSelectionTest {

  @Test
  void redis_bean_requires_itsm_redis_enabled_true() throws Exception {
    var method = CacheConfiguration.class.getDeclaredMethod(
        "redisCachePort",
        org.springframework.data.redis.core.StringRedisTemplate.class,
        com.fasterxml.jackson.databind.ObjectMapper.class,
        ItsmRedisProperties.class
    );
    ConditionalOnProperty property = method.getAnnotation(ConditionalOnProperty.class);
    assertThat(property).isNotNull();
    assertThat(property.prefix()).isEqualTo("itsm.redis");
    assertThat(property.name()).containsExactly("enabled");
    assertThat(property.havingValue()).isEqualTo("true");
  }

  @Test
  void concurrent_map_is_missing_bean_fallback() throws Exception {
    var method = CacheConfiguration.class.getDeclaredMethod("concurrentMapCachePort");
    ConditionalOnMissingBean missing = method.getAnnotation(ConditionalOnMissingBean.class);
    assertThat(missing).isNotNull();
    assertThat(missing.value()).containsExactly(CachePort.class);
  }

  @Test
  void default_redis_properties_are_disabled() {
    ItsmRedisProperties props = new ItsmRedisProperties();
    assertThat(props.isEnabled()).isFalse();
    assertThat(props.getHost()).isEqualTo("localhost");
    assertThat(props.getPort()).isEqualTo(6379);
    assertThat(props.getKeyPrefix()).isEqualTo("itsm:cache:");
  }

  @Test
  void cache_configuration_is_a_spring_configuration() {
    StandardAnnotationMetadata meta = new StandardAnnotationMetadata(CacheConfiguration.class);
    assertThat(meta.hasAnnotation("org.springframework.context.annotation.Configuration")).isTrue();
  }
}
