package ru.ultimavox.itsm.platform.security;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

final class TokenBucketRateLimiter {
  record Decision(boolean allowed, long remaining, long retryAfterSeconds) {}

  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
  private final long capacity;
  private final long refillTokens;
  private final long refillPeriodNanos;
  private final LongSupplier nanoClock;

  TokenBucketRateLimiter(long capacity, long refillTokens, Duration refillPeriod, LongSupplier nanoClock) {
    if (capacity < 1) {
      throw new IllegalArgumentException("capacity must be >= 1");
    }
    if (refillTokens < 1) {
      throw new IllegalArgumentException("refillTokens must be >= 1");
    }
    this.capacity = capacity;
    this.refillTokens = refillTokens;
    this.refillPeriodNanos = refillPeriod.toNanos();
    this.nanoClock = nanoClock;
  }

  Decision consume(String key) {
    long now = nanoClock.getAsLong();
    Holder holder = new Holder();
    buckets.compute(key, (ignored, current) -> {
      Bucket bucket = current == null ? new Bucket(capacity, now) : refill(current, now);
      if (bucket.tokens >= 1) {
        long remaining = bucket.tokens - 1;
        holder.decision = new Decision(true, remaining, 0);
        return new Bucket(remaining, bucket.lastRefillNanos);
      }
      long elapsed = now - bucket.lastRefillNanos;
      long retryNanos = Math.max(1, refillPeriodNanos - elapsed);
      holder.decision = new Decision(false, 0, Math.max(1, (retryNanos + 999_999_999L) / 1_000_000_000L));
      return bucket;
    });
    return holder.decision;
  }

  private Bucket refill(Bucket current, long now) {
    long elapsed = now - current.lastRefillNanos;
    if (elapsed < refillPeriodNanos) {
      return current;
    }
    long periods = elapsed / refillPeriodNanos;
    long tokens = Math.min(capacity, current.tokens + periods * refillTokens);
    return new Bucket(tokens, current.lastRefillNanos + periods * refillPeriodNanos);
  }

  private static final class Bucket {
    private final long tokens;
    private final long lastRefillNanos;

    private Bucket(long tokens, long lastRefillNanos) {
      this.tokens = tokens;
      this.lastRefillNanos = lastRefillNanos;
    }
  }

  private static final class Holder {
    private Decision decision = new Decision(false, 0, 1);
  }
}
