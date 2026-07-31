package ru.ultimavox.itsm.platform.automation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.automation.AutomationRule.Action;
import ru.ultimavox.itsm.platform.event.DomainEvent;

import java.util.List;
import java.util.Map;

/**
 * In-process, transactional automation runner.
 * Evaluates enabled rules matching a {@link DomainEvent} and executes allowlisted actions
 * with an idempotent action log.
 */
@Service
public class AutomationRunner {

    private static final Logger log = LoggerFactory.getLogger(AutomationRunner.class);

    private final AutomationRuleRepository rules;
    private final ConditionEvaluator conditions;
    private final AllowlistedActionExecutor executor;
    private final AutomationActionLogRepository actionLog;

    public AutomationRunner(
            AutomationRuleRepository rules,
            ConditionEvaluator conditions,
            AllowlistedActionExecutor executor,
            AutomationActionLogRepository actionLog
    ) {
        this.rules = rules;
        this.conditions = conditions;
        this.executor = executor;
        this.actionLog = actionLog;
    }

    @Transactional
    public int handle(DomainEvent event) {
        if (event == null || event.type() == null) {
            return 0;
        }
        List<AutomationRule> matching = rules.findEnabledByEventType(event.type());
        int executed = 0;
        for (AutomationRule rule : matching) {
            if (!conditions.matches(event, rule.conditions())) {
                continue;
            }
            for (Action action : rule.actions()) {
                boolean firstTime = actionLog.tryLog(
                        rule.ruleKey(),
                        event.id(),
                        action.type(),
                        "STARTED",
                        Map.of("eventType", event.type(), "aggregateId", event.aggregateId())
                );
                if (!firstTime) {
                    log.debug("Skipping already-executed action {} for rule {} event {}",
                            action.type(), rule.ruleKey(), event.id());
                    continue;
                }
                try {
                    executor.execute(action, event);
                    executed++;
                } catch (RuntimeException ex) {
                    log.error("Automation action failed rule={} action={} event={}: {}",
                            rule.ruleKey(), action.type(), event.id(), ex.getMessage());
                    // row already logged as STARTED; leave for operator review / future retry table
                }
            }
        }
        return executed;
    }
}
