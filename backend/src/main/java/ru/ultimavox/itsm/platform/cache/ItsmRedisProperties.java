package ru.ultimavox.itsm.platform.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis wiring for {@link CachePort}. Disabled by default so local/tests need no Redis process.
 * When enabled, {@link CacheConfiguration} prefers Redis and falls back to the concurrent-map
 * adapter on connection or command failures.
 */
@ConfigurationProperties(prefix = "itsm.redis")
public class ItsmRedisProperties {

  /** When true, {@link RedisCachePort} is primary (with concurrent-map fallback). */
  private boolean enabled = false;

  private String host = "localhost";

  private int port = 6379;

  private String password = "";

  private int database = 0;

  private String keyPrefix = "itsm:cache:";

  private Duration timeout = Duration.ofSeconds(2);

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public int getDatabase() {
    return database;
  }

  public void setDatabase(int database) {
    this.database = database;
  }

  public String getKeyPrefix() {
    return keyPrefix;
  }

  public void setKeyPrefix(String keyPrefix) {
    this.keyPrefix = keyPrefix;
  }

  public Duration getTimeout() {
    return timeout;
  }

  public void setTimeout(Duration timeout) {
    this.timeout = timeout;
  }
}
