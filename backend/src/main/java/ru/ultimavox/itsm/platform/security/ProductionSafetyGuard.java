package ru.ultimavox.itsm.platform.security;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fail-fast when insecure development authentication is combined with a production-like profile.
 *
 * <p>Triggers when active profiles include both {@code dev} and any of
 * {@code prod}, {@code production}, {@code staging}, or when
 * {@code itsm.security.permit-unauthenticated=true} under those production-like profiles.
 */
@Component
public class ProductionSafetyGuard implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(ProductionSafetyGuard.class);

  private static final Set<String> PRODUCTION_LIKE = Set.of("prod", "production", "staging");

  private final Environment environment;
  private final boolean permitUnauthenticated;

  public ProductionSafetyGuard(
      Environment environment,
      @Value("${itsm.security.permit-unauthenticated:false}") boolean permitUnauthenticated
  ) {
    this.environment = environment;
    this.permitUnauthenticated = permitUnauthenticated;
  }

  @Override
  public void run(ApplicationArguments args) {
    Set<String> profiles = Arrays.stream(environment.getActiveProfiles())
        .map(p -> p.toLowerCase(Locale.ROOT))
        .collect(Collectors.toSet());

    boolean prodLike = profiles.stream().anyMatch(PRODUCTION_LIKE::contains);
    boolean dev = profiles.contains("dev");

    if (prodLike && dev) {
      fail(
          "Refusing to start: Spring profile 'dev' (insecure authentication) is active "
              + "together with a production-like profile " + profiles
      );
    }
    if (prodLike && permitUnauthenticated) {
      fail(
          "Refusing to start: itsm.security.permit-unauthenticated=true under production-like "
              + "profiles " + profiles
      );
    }
    if (dev) {
      log.warn("Profile 'dev' active — OIDC JWT enforcement disabled (local only)");
    }
  }

  private static void fail(String message) {
    log.error(message);
    throw new IllegalStateException(message);
  }
}
