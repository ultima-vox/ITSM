package ru.ultimavox.itsm.platform.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Provides {@link CachePort}.
 *
 * <ul>
 *   <li>{@code itsm.redis.enabled=true} — Redis primary + concurrent-map fallback on failure</li>
 *   <li>otherwise — in-process {@link ConcurrentMapCachePort} only</li>
 * </ul>
 *
 * <p>Spring Boot Redis auto-configuration is excluded in {@code application.yml} so disabled
 * environments never open a Redis connection.
 */
@Configuration
@EnableConfigurationProperties(ItsmRedisProperties.class)
class CacheConfiguration {

  @Bean(destroyMethod = "destroy")
  @ConditionalOnProperty(prefix = "itsm.redis", name = "enabled", havingValue = "true")
  LettuceConnectionFactory itsmRedisConnectionFactory(ItsmRedisProperties props) {
    RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration();
    standalone.setHostName(props.getHost());
    standalone.setPort(props.getPort());
    standalone.setDatabase(props.getDatabase());
    if (props.getPassword() != null && !props.getPassword().isBlank()) {
      standalone.setPassword(RedisPassword.of(props.getPassword()));
    }
    Duration timeout = props.getTimeout() == null ? Duration.ofSeconds(2) : props.getTimeout();
    LettuceClientConfiguration client = LettuceClientConfiguration.builder()
        .commandTimeout(timeout)
        .build();
    LettuceConnectionFactory factory = new LettuceConnectionFactory(standalone, client);
    factory.setValidateConnection(false);
    factory.afterPropertiesSet();
    return factory;
  }

  @Bean
  @ConditionalOnProperty(prefix = "itsm.redis", name = "enabled", havingValue = "true")
  StringRedisTemplate itsmStringRedisTemplate(LettuceConnectionFactory itsmRedisConnectionFactory) {
    return new StringRedisTemplate(itsmRedisConnectionFactory);
  }

  @Bean
  @ConditionalOnProperty(prefix = "itsm.redis", name = "enabled", havingValue = "true")
  CachePort redisCachePort(
      StringRedisTemplate itsmStringRedisTemplate,
      ObjectMapper objectMapper,
      ItsmRedisProperties props
  ) {
    CachePort redis = new RedisCachePort(itsmStringRedisTemplate, objectMapper, props.getKeyPrefix());
    return new FallbackCachePort(redis, new ConcurrentMapCachePort());
  }

  @Bean
  @ConditionalOnMissingBean(CachePort.class)
  CachePort concurrentMapCachePort() {
    return new ConcurrentMapCachePort();
  }
}
