package ru.ultimavox.itsm.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(0)
@ConditionalOnProperty(name = "itsm.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
final class RateLimitFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

  private final TokenBucketRateLimiter limiter;
  private final long limit;

  @Autowired
  RateLimitFilter(
      @Value("${itsm.rate-limit.capacity:120}") long capacity,
      @Value("${itsm.rate-limit.refill-tokens:60}") long refillTokens,
      @Value("${itsm.rate-limit.refill-period:PT1M}") Duration refillPeriod
  ) {
    this.limiter = new TokenBucketRateLimiter(capacity, refillTokens, refillPeriod, System::nanoTime);
    this.limit = capacity;
  }

  RateLimitFilter(TokenBucketRateLimiter limiter, long limit) {
    this.limiter = limiter;
    this.limit = limit;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (HttpMethod.OPTIONS.matches(request.getMethod())) {
      return true;
    }
    String path = request.getRequestURI();
    return path.startsWith("/actuator/health");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    TokenBucketRateLimiter.Decision decision = limiter.consume(key(request));
    response.setHeader("X-RateLimit-Limit", Long.toString(limit));
    response.setHeader("X-RateLimit-Remaining", Long.toString(decision.remaining()));
    if (!decision.allowed()) {
      response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
      response.setStatus(429);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      log.warn("rate-limited method={} path={} key={}", request.getMethod(), request.getRequestURI(), key(request));
      response.getWriter().write("{\"status\":429,\"title\":\"Too Many Requests\"}");
      return;
    }
    chain.doFilter(request, response);
  }

  private static String key(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && authentication.getName() != null
        && !"anonymousUser".equals(authentication.getName())) {
      return "principal:" + authentication.getName();
    }
    return "ip:" + request.getRemoteAddr();
  }
}
