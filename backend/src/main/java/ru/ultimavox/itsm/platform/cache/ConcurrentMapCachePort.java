package ru.ultimavox.itsm.platform.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Zero-dependency fallback when Redis is not wired. Suitable for single-node dev/demo only.
 * Entries expire lazily on read.
 */
public final class ConcurrentMapCachePort implements CachePort {

  private record Entry(Object value, Instant expiresAt) {
    boolean expired() {
      return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
  }

  private final ConcurrentMap<String, Entry> store = new ConcurrentHashMap<>();

  @Override
  @SuppressWarnings("unchecked")
  public <T> Optional<T> get(String key, Class<T> type) {
    Entry entry = store.get(key);
    if (entry == null) {
      return Optional.empty();
    }
    if (entry.expired()) {
      store.remove(key, entry);
      return Optional.empty();
    }
    if (!type.isInstance(entry.value())) {
      return Optional.empty();
    }
    return Optional.of((T) entry.value());
  }

  @Override
  public void put(String key, Object value, Duration ttl) {
    Instant expires = ttl == null || ttl.isZero() || ttl.isNegative()
        ? null
        : Instant.now().plus(ttl);
    store.put(key, new Entry(value, expires));
  }

  @Override
  public void evict(String key) {
    store.remove(key);
  }

  @Override
  public void clear() {
    store.clear();
  }
}
