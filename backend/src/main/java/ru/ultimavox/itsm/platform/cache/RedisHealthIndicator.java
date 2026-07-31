package ru.ultimavox.itsm.platform.cache;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Actuator health for Redis when {@code itsm.redis.enabled=true}. */
@Component("redisCache")
@ConditionalOnProperty(prefix = "itsm.redis", name = "enabled", havingValue = "true")
class RedisHealthIndicator implements HealthIndicator {

  private final StringRedisTemplate redis;
  private final ItsmRedisProperties props;

  RedisHealthIndicator(StringRedisTemplate itsmStringRedisTemplate, ItsmRedisProperties props) {
    this.redis = itsmStringRedisTemplate;
    this.props = props;
  }

  @Override
  public Health health() {
    try {
      String pong = redis.getConnectionFactory() == null
          ? null
          : redis.getConnectionFactory().getConnection().ping();
      if (pong != null && !pong.isBlank()) {
        return Health.up()
            .withDetail("host", props.getHost())
            .withDetail("port", props.getPort())
            .withDetail("database", props.getDatabase())
            .withDetail("ping", pong)
            .build();
      }
      return Health.down()
          .withDetail("host", props.getHost())
          .withDetail("port", props.getPort())
          .withDetail("reason", "empty PING")
          .build();
    } catch (Exception ex) {
      return Health.down(ex)
          .withDetail("host", props.getHost())
          .withDetail("port", props.getPort())
          .build();
    }
  }
}
