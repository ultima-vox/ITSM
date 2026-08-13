package ru.ultimavox.itsm.platform.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AutomationDepthContextTest {

  @Test
  void defaultsToZeroOutsideAutomation() {
    assertThat(AutomationDepthContext.current()).isZero();
  }

  @Test
  void restoresPreviousDepthAfterNestedExecution() {
    AutomationDepthContext.atDepth(2, () -> {
      assertThat(AutomationDepthContext.current()).isEqualTo(2);
      AutomationDepthContext.atDepth(4, () -> {
        assertThat(AutomationDepthContext.current()).isEqualTo(4);
        return null;
      });
      assertThat(AutomationDepthContext.current()).isEqualTo(2);
      return null;
    });
    assertThat(AutomationDepthContext.current()).isZero();
  }

  @Test
  void restoresDepthEvenWhenActionThrows() {
    assertThatThrownBy(() -> AutomationDepthContext.atDepth(1, () -> {
      throw new IllegalStateException("boom");
    })).isInstanceOf(IllegalStateException.class);
    assertThat(AutomationDepthContext.current()).isZero();
  }

  @Test
  void supportsParallelThreadsIndependently() throws InterruptedException {
    AtomicInteger observed = new AtomicInteger();
    Thread worker = new Thread(() -> {
      AutomationDepthContext.atDepth(5, () -> {
        observed.set(AutomationDepthContext.current());
        return null;
      });
    });
    worker.start();
    worker.join();
    assertThat(observed.get()).isEqualTo(5);
    assertThat(AutomationDepthContext.current()).isZero();
  }
}
