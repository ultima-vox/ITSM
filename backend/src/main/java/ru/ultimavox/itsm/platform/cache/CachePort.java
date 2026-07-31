package ru.ultimavox.itsm.platform.cache;

import java.time.Duration;
import java.util.Optional;

/**
 * Lightweight cache port. Default implementation is an in-process concurrent map;
 * Redis (or another distributed backend) can replace the bean without touching callers.
 */
public interface CachePort {

  <T> Optional<T> get(String key, Class<T> type);

  void put(String key, Object value, Duration ttl);

  void evict(String key);

  void clear();
}
