package ru.ultimavox.itsm.platform.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FallbackCachePortTest {

  @Test
  void uses_primary_when_healthy() {
    CachePort primary = new ConcurrentMapCachePort();
    CachePort fallback = new ConcurrentMapCachePort();
    FallbackCachePort cache = new FallbackCachePort(primary, fallback);

    cache.put("k", "from-primary", Duration.ofMinutes(1));
    assertThat(cache.get("k", String.class)).contains("from-primary");
    assertThat(primary.get("k", String.class)).contains("from-primary");
    assertThat(fallback.get("k", String.class)).isEmpty();
    assertThat(cache.isPrimaryHealthy()).isTrue();
  }

  @Test
  void degrades_to_fallback_when_primary_fails() {
    AtomicInteger gets = new AtomicInteger();
    CachePort primary = new CachePort() {
      @Override
      public <T> Optional<T> get(String key, Class<T> type) {
        gets.incrementAndGet();
        throw new IllegalStateException("redis down");
      }

      @Override
      public void put(String key, Object value, Duration ttl) {
        throw new IllegalStateException("redis down");
      }

      @Override
      public void evict(String key) {
        throw new IllegalStateException("redis down");
      }

      @Override
      public void clear() {
        throw new IllegalStateException("redis down");
      }
    };
    CachePort fallback = new ConcurrentMapCachePort();
    FallbackCachePort cache = new FallbackCachePort(primary, fallback);

    cache.put("k", "v", Duration.ofMinutes(1));
    assertThat(cache.get("k", String.class)).contains("v");
    assertThat(fallback.get("k", String.class)).contains("v");
    assertThat(cache.isPrimaryHealthy()).isFalse();
    assertThat(gets.get()).isGreaterThanOrEqualTo(1);
  }
}
