package ru.ultimavox.itsm.platform.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ConcurrentMapCachePortTest {

  @Test
  void put_get_and_evict() {
    CachePort cache = new ConcurrentMapCachePort();
    cache.put("k", "v", Duration.ofMinutes(5));
    assertThat(cache.get("k", String.class)).contains("v");
    cache.evict("k");
    assertThat(cache.get("k", String.class)).isEmpty();
  }

  @Test
  void type_mismatch_returns_empty() {
    CachePort cache = new ConcurrentMapCachePort();
    cache.put("n", 42, Duration.ofMinutes(1));
    assertThat(cache.get("n", String.class)).isEmpty();
    assertThat(cache.get("n", Integer.class)).contains(42);
  }
}
