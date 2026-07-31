package ru.ultimavox.itsm.platform.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uses {@code primary} when healthy; on any failure degrades to {@code fallback}
 * (typically {@link ConcurrentMapCachePort}) so request paths keep working without Redis.
 */
final class FallbackCachePort implements CachePort {

  private static final Logger log = LoggerFactory.getLogger(FallbackCachePort.class);

  private final CachePort primary;
  private final CachePort fallback;
  private final AtomicBoolean primaryHealthy = new AtomicBoolean(true);

  FallbackCachePort(CachePort primary, CachePort fallback) {
    this.primary = primary;
    this.fallback = fallback;
  }

  boolean isPrimaryHealthy() {
    return primaryHealthy.get();
  }

  @Override
  public <T> Optional<T> get(String key, Class<T> type) {
    try {
      Optional<T> value = primary.get(key, type);
      markHealthy();
      return value;
    } catch (RuntimeException ex) {
      markDegraded(ex);
      return fallback.get(key, type);
    }
  }

  @Override
  public void put(String key, Object value, Duration ttl) {
    try {
      primary.put(key, value, ttl);
      markHealthy();
    } catch (RuntimeException ex) {
      markDegraded(ex);
      fallback.put(key, value, ttl);
    }
  }

  @Override
  public void evict(String key) {
    try {
      primary.evict(key);
      markHealthy();
    } catch (RuntimeException ex) {
      markDegraded(ex);
      fallback.evict(key);
    }
  }

  @Override
  public void clear() {
    try {
      primary.clear();
      markHealthy();
    } catch (RuntimeException ex) {
      markDegraded(ex);
      fallback.clear();
    }
  }

  private void markHealthy() {
    if (primaryHealthy.compareAndSet(false, true)) {
      log.info("Primary cache recovered");
    }
  }

  private void markDegraded(RuntimeException ex) {
    if (primaryHealthy.compareAndSet(true, false)) {
      log.warn("Primary cache unavailable; using in-process fallback: {}", ex.toString());
    } else {
      log.debug("Primary cache still unavailable: {}", ex.toString());
    }
  }
}
