package ru.ultimavox.itsm.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {

  @Test
  void rejectsAfterCapacityThenRefills() {
    AtomicLong now = new AtomicLong(0);
    TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2, 2, Duration.ofSeconds(60), now::get);

    assertThat(limiter.consume("alice").allowed()).isTrue();
    assertThat(limiter.consume("alice").allowed()).isTrue();
    TokenBucketRateLimiter.Decision denied = limiter.consume("alice");
    assertThat(denied.allowed()).isFalse();
    assertThat(denied.retryAfterSeconds()).isEqualTo(60);

    now.addAndGet(Duration.ofSeconds(60).toNanos());
    TokenBucketRateLimiter.Decision afterRefill = limiter.consume("alice");
    assertThat(afterRefill.allowed()).isTrue();
    assertThat(afterRefill.remaining()).isEqualTo(1);
  }

  @Test
  void isolatesBucketsPerKey() {
    AtomicLong now = new AtomicLong(0);
    TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1, Duration.ofSeconds(60), now::get);
    assertThat(limiter.consume("a").allowed()).isTrue();
    assertThat(limiter.consume("a").allowed()).isFalse();
    assertThat(limiter.consume("b").allowed()).isTrue();
  }
}
