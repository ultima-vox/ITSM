package ru.ultimavox.itsm.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

  @Test
  void returns429WhenBucketEmpty() throws Exception {
    AtomicLong now = new AtomicLong(0);
    TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1, Duration.ofSeconds(30), now::get);
    RateLimitFilter filter = new RateLimitFilter(limiter, 1);
    FilterChain chain = mock(FilterChain.class);

    MockHttpServletRequest first = new MockHttpServletRequest("GET", "/api/v1/work-items");
    first.setRemoteAddr("10.0.0.8");
    MockHttpServletResponse ok = new MockHttpServletResponse();
    filter.doFilter(first, ok, chain);
    verify(chain).doFilter(first, ok);
    assertThat(ok.getHeader("X-RateLimit-Remaining")).isEqualTo("0");

    MockHttpServletRequest second = new MockHttpServletRequest("GET", "/api/v1/work-items");
    second.setRemoteAddr("10.0.0.8");
    MockHttpServletResponse limited = new MockHttpServletResponse();
    filter.doFilter(second, limited, chain);
    verify(chain, never()).doFilter(second, limited);
    assertThat(limited.getStatus()).isEqualTo(429);
    assertThat(limited.getHeader("Retry-After")).isEqualTo("30");
    assertThat(limited.getContentAsString()).contains("Too Many Requests");
  }

  @Test
  void skipsHealthAndOptions() throws Exception {
    TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1, Duration.ofSeconds(30), () -> 0L);
    RateLimitFilter filter = new RateLimitFilter(limiter, 1);
    FilterChain chain = mock(FilterChain.class);

    MockHttpServletRequest health = new MockHttpServletRequest("GET", "/actuator/health");
    health.setRequestURI("/actuator/health");
    MockHttpServletResponse healthResponse = new MockHttpServletResponse();
    filter.doFilter(health, healthResponse, chain);

    MockHttpServletRequest preflight = new MockHttpServletRequest("OPTIONS", "/api/v1/work-items");
    preflight.setRequestURI("/api/v1/work-items");
    MockHttpServletResponse preflightResponse = new MockHttpServletResponse();
    filter.doFilter(preflight, preflightResponse, chain);

    verify(chain, times(1)).doFilter(health, healthResponse);
    verify(chain, times(1)).doFilter(preflight, preflightResponse);
    assertThat(healthResponse.getStatus()).isEqualTo(200);
    assertThat(limiter.consume("ip:127.0.0.1").allowed()).isTrue();
  }
}
