package ru.ultimavox.itsm.platform.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.Environment;

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
    Environment env = mock(Environment.class);
    when(env.getActiveProfiles()).thenReturn(new String[] {"prod"});
    ProductionSafetyGuard guard = new ProductionSafetyGuard(env, false);
    assertThatCode(() -> guard.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
  }
}
