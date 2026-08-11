package ru.ultimavox.itsm.platform.workflow;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.ultimavox.itsm.platform.workflow.WorkflowTimerProcessor.Result;

/** Polls durable timers. Claims commit before execution, so expired leases recover node crashes. */
@Component
class WorkflowTimerRunner {
  private final WorkflowTimerService timers;
  private final WorkflowTimerProcessor processor;

  WorkflowTimerRunner(WorkflowTimerService timers, WorkflowTimerProcessor processor) {
    this.timers = timers;
    this.processor = processor;
  }

  @Scheduled(fixedDelayString = "${itsm.workflow.timers.poll-interval:PT5S}")
  void poll() {
    for (var timer : timers.claimDue(100, 60)) {
      try {
        Result result = processor.execute(timer);
        if (result == Result.COMPLETED) timers.complete(timer.id(), timer.orgId());
        else timers.cancel(timer.id(), timer.orgId(), "Workflow instance changed before timer fired");
      } catch (RuntimeException failure) {
        timers.retryOrDead(timer, failure);
      }
    }
  }
}
