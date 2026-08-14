package ru.ultimavox.itsm.platform.automation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AutomationActionRetryServiceTest {

  @Test
  void retryDelayGrowsExponentiallyAndCaps() {
    Duration base = Duration.ofSeconds(30);
    Duration max = Duration.ofMinutes(5);
    assertThat(AutomationActionRetryService.retryDelay(base, max, 1)).isEqualTo(Duration.ofSeconds(30));
    assertThat(AutomationActionRetryService.retryDelay(base, max, 2)).isEqualTo(Duration.ofMinutes(1));
    assertThat(AutomationActionRetryService.retryDelay(base, max, 3)).isEqualTo(Duration.ofMinutes(2));
    assertThat(AutomationActionRetryService.retryDelay(base, max, 4)).isEqualTo(Duration.ofMinutes(4));
    assertThat(AutomationActionRetryService.retryDelay(base, max, 5)).isEqualTo(Duration.ofMinutes(5));
    assertThat(AutomationActionRetryService.retryDelay(base, max, 10)).isEqualTo(Duration.ofMinutes(5));
  }
}
