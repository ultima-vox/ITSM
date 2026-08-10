package ru.ultimavox.itsm.platform.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

class ProductionSafetyGuardTest {

  @Test
  void fails_when_dev_and_prod_combined() {
    Environment env = mock(Environment.class);
    when(env.getActiveProfiles()).thenReturn(new String[] {"dev", "prod"});
    ProductionSafetyGuard guard = new ProductionSafetyGuard(env, false);
    assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("dev");
  }

  @Test
  void fails_when_permit_unauthenticated_under_staging() {
    Environment env = mock(Environment.class);
    when(env.getActiveProfiles()).thenReturn(new String[] {"staging"});
    ProductionSafetyGuard guard = new ProductionSafetyGuard(env, true);
    assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("permit-unauthenticated");
  }

  @Test
  void allows_dev_alone() {
    Environment env = mock(Environment.class);
    when(env.getActiveProfiles()).thenReturn(new String[] {"dev"});
    ProductionSafetyGuard guard = new ProductionSafetyGuard(env, true);
    assertThatCode(() -> guard.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
  }

  @Test
  void allows_prod_with_secure_defaults() {
    MockEnvironment env = secureProductionEnvironment();
    ProductionSafetyGuard guard = new ProductionSafetyGuard(env, false);
    assertThatCode(() -> guard.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
  }

  @Test
  void fails_prod_with_demo_database_password() {
    MockEnvironment env = secureProductionEnvironment()
        .withProperty("spring.datasource.password", "itsm");
    ProductionSafetyGuard guard = new ProductionSafetyGuard(env, false);
    assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments()))
        .hasMessageContaining("spring.datasource.password");
  }

  @Test
  void fails_prod_with_http_oidc_issuer() {
    MockEnvironment env = secureProductionEnvironment()
        .withProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri", "http://idp/internal");
    ProductionSafetyGuard guard = new ProductionSafetyGuard(env, false);
    assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments()))
        .hasMessageContaining("OIDC issuer");
  }

  @Test
  void fails_prod_with_localhost_cors() {
    MockEnvironment env = secureProductionEnvironment()
        .withProperty("itsm.cors.allowed-origins", "http://localhost:5173");
    ProductionSafetyGuard guard = new ProductionSafetyGuard(env, false);
    assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments()))
        .hasMessageContaining("CORS origins");
  }

  @Test
  void fails_prod_with_demo_s3_secret() {
    MockEnvironment env = secureProductionEnvironment()
        .withProperty("itsm.storage.type", "s3")
        .withProperty("itsm.storage.s3.secret-key", "minioadmin");
    ProductionSafetyGuard guard = new ProductionSafetyGuard(env, false);
    assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments()))
        .hasMessageContaining("itsm.storage.s3.secret-key");
  }

  private static MockEnvironment secureProductionEnvironment() {
    MockEnvironment env = new MockEnvironment()
        .withProperty("spring.datasource.password", "strong-database-secret")
        .withProperty(
            "spring.security.oauth2.resourceserver.jwt.issuer-uri", "https://id.example.test/realm")
        .withProperty("itsm.cors.allowed-origins", "https://desk.example.test")
        .withProperty("itsm.storage.s3.access-key", "production-access")
        .withProperty("itsm.storage.s3.secret-key", "production-secret");
    env.setActiveProfiles("prod");
    return env;
  }
}
