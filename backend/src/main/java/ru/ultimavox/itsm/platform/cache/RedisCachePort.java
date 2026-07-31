package ru.ultimavox.itsm.platform.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis-backed {@link CachePort}. Values are JSON-serialized. Failures propagate so
 * {@link FallbackCachePort} can degrade to the concurrent-map adapter.
 */
final class RedisCachePort implements CachePort {

  private final StringRedisTemplate redis;
  private final ObjectMapper json;
  private final String keyPrefix;

  RedisCachePort(StringRedisTemplate redis, ObjectMapper json, String keyPrefix) {
    this.redis = redis;
    this.json = json;
    this.keyPrefix = keyPrefix == null || keyPrefix.isBlank() ? "itsm:cache:" : keyPrefix;
  }

  @Override
  public <T> Optional<T> get(String key, Class<T> type) {
    String raw = redis.opsForValue().get(prefixed(key));
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    try {
      T value = json.readValue(raw, type);
      return Optional.ofNullable(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Cache deserialize failed for key=" + key, ex);
    }
  }

  @Override
  public void put(String key, Object value, Duration ttl) {
    try {
      String payload = json.writeValueAsString(value);
      if (ttl == null || ttl.isZero() || ttl.isNegative()) {
        redis.opsForValue().set(prefixed(key), payload);
      } else {
        redis.opsForValue().set(prefixed(key), payload, ttl);
      }
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Cache serialize failed for key=" + key, ex);
    }
  }

  @Override
  public void evict(String key) {
    redis.delete(prefixed(key));
  }

  @Override
  public void clear() {
    // Intentionally narrow: never FLUSHDB — only keys under our prefix would be safe.
    // Callers that need a full wipe should use a dedicated admin path.
    throw new UnsupportedOperationException("RedisCachePort.clear() is not supported (avoid FLUSHDB)");
  }

  private String prefixed(String key) {
    return keyPrefix + key;
  }
}
