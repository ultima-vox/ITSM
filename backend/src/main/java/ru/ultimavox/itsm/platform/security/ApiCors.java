package ru.ultimavox.itsm.platform.security;

import java.util.List;

final class ApiCors {
  private ApiCors() {}

  static List<String> allowedHeaders() {
    return List.of(
        "Authorization",
        "Content-Type",
        "Accept",
        "X-Requested-With",
        "X-Correlation-ID",
        "Idempotency-Key");
  }

  static List<String> exposedHeaders() {
    return List.of(
        "Location",
        "X-Correlation-ID",
        "X-RateLimit-Limit",
        "X-RateLimit-Remaining",
        "Retry-After");
  }
}
