package ru.ultimavox.itsm.platform.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

class OrganizationContextTest {
  @AfterEach
  void clear() { SecurityContextHolder.clearContext(); }

  @Test
  void trustedOverrideIsNestedAndAlwaysRestored() {
    assertThat(OrganizationContext.current()).isEqualTo("default");
    assertThat(OrganizationContext.runAs("tenant-a", () -> {
      assertThat(OrganizationContext.current()).isEqualTo("tenant-a");
      return OrganizationContext.runAs("tenant-b", OrganizationContext::current);
    })).isEqualTo("tenant-b");
    assertThat(OrganizationContext.current()).isEqualTo("default");

    assertThatThrownBy(() -> OrganizationContext.runAs("tenant-a", () -> {
      throw new IllegalStateException("boom");
    })).isInstanceOf(IllegalStateException.class);
    assertThat(OrganizationContext.current()).isEqualTo("default");
  }
}
