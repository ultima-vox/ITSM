package ru.ultimavox.itsm.platform.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
final class CorrelationIdFilter extends OncePerRequestFilter {
  static final String HEADER = "X-Correlation-ID";
  static final String MDC_KEY = "correlationId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    UUID correlationId = parse(request.getHeader(HEADER));
    CorrelationContext.set(correlationId);
    MDC.put(MDC_KEY, correlationId.toString());
    response.setHeader(HEADER, correlationId.toString());
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
      CorrelationContext.clear();
    }
  }

  private UUID parse(String candidate) {
    if (candidate != null) {
      try {
        return UUID.fromString(candidate);
      } catch (IllegalArgumentException ignored) {
        // Reject malformed propagation by replacing it with a trusted UUID.
      }
    }
    return UUID.randomUUID();
  }
}
