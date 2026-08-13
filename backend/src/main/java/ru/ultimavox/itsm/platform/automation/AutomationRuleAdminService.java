package ru.ultimavox.itsm.platform.automation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

/** Validates and persists tenant automation rules with optimistic concurrency. */
@Service
public class AutomationRuleAdminService {
    private final AutomationRuleRepository repository;
    private final ObjectMapper json;
    private final AuditTrail audit;
    private final IntegrationEventOutbox outbox;
    private final AllowlistedActionExecutor executor;

    public AutomationRuleAdminService(AutomationRuleRepository repository, ObjectMapper json,
                                      AuditTrail audit, IntegrationEventOutbox outbox,
                                      AllowlistedActionExecutor executor) {
        this.repository = repository;
        this.json = json;
        this.audit = audit;
        this.outbox = outbox;
        this.executor = executor;
    }

    @Transactional
    public AutomationRule create(String actor, Command command) {
        AutomationRule candidate = validate(null, 1, command);
        AutomationRule saved;
        try {
            saved = repository.create(candidate, serialize(candidate));
        } catch (DataIntegrityViolationException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Automation rule key already exists");
        }
        record(actor, "automation.rule-created", saved);
        return saved;
    }

    @Transactional
    public AutomationRule update(String actor, UUID id, int expectedVersion, Command command) {
        if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion must be positive");
        AutomationRule candidate = validate(id, expectedVersion + 1, command);
        AutomationRule saved = repository.update(id, expectedVersion, candidate, serialize(candidate))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Automation rule changed or does not exist"));
        record(actor, "automation.rule-updated", saved);
        return saved;
    }

    @Transactional
    public AutomationRule setEnabled(String actor, UUID id, boolean enabled) {
        AutomationRule saved = repository.setEnabled(id, enabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Automation rule not found"));
        record(actor, enabled ? "automation.rule-enabled" : "automation.rule-disabled", saved);
        return saved;
    }

    private AutomationRule validate(UUID id, int version, Command command) {
        if (command == null) throw new IllegalArgumentException("Rule body is required");
        if (command.ruleKey() == null || !command.ruleKey().matches("[a-z][a-z0-9.-]{0,99}")) {
            throw new IllegalArgumentException("Invalid rule key");
        }
        if (command.name() == null || command.name().isBlank() || command.name().length() > 200) {
            throw new IllegalArgumentException("Rule name is required and limited to 200 characters");
        }
        if (command.trigger() == null) throw new IllegalArgumentException("Rule trigger is required");
        List<AutomationRule.Condition> conditions = command.conditions() == null ? List.of() : List.copyOf(command.conditions());
        List<AutomationRule.Action> actions = command.actions() == null ? List.of() : List.copyOf(command.actions());
        if (conditions.size() > 50 || actions.isEmpty() || actions.size() > 20) {
            throw new IllegalArgumentException("Rule allows up to 50 conditions and requires 1 to 20 actions");
        }
        for (AutomationRule.Condition condition : conditions) {
            if (condition.field() == null || !condition.field().matches("[a-zA-Z][a-zA-Z0-9_.-]{0,199}")
                    || condition.operator() == null || condition.value() == null || condition.value().length() > 2_000) {
                throw new IllegalArgumentException("Invalid automation condition");
            }
        }
        HashSet<String> actionTypes = new HashSet<>();
        for (AutomationRule.Action action : actions) {
            if (!executor.supports(action.type())) {
                throw new IllegalArgumentException("Action type not allowlisted: " + action.type());
            }
            if (!actionTypes.add(action.type())) {
                throw new IllegalArgumentException("Duplicate action type is ambiguous for idempotency: " + action.type());
            }
            if (action.parameters().size() > 50) throw new IllegalArgumentException("Too many action parameters");
        }
        return new AutomationRule(id, command.ruleKey(), command.name().trim(), version,
                command.enabled(), command.trigger(), conditions, actions);
    }

    private String serialize(AutomationRule rule) {
        try {
            return json.writeValueAsString(Map.of("name", rule.name(), "trigger", rule.trigger(),
                    "conditions", rule.conditions(), "actions", rule.actions()));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize automation rule", ex);
        }
    }

    private void record(String actor, String action, AutomationRule rule) {
        Instant now = Instant.now();
        UUID correlation = UUID.randomUUID();
        Map<String, Object> details = Map.of("version", rule.version(), "enabled", rule.enabled());
        audit.append(new AuditTrail.Entry(actor, action, "automation_rule", rule.ruleKey(),
                Map.of(), details, correlation, now));
        outbox.record(new DomainEvent(UUID.randomUUID(), action, 1, now, correlation,
                "automation_rule", rule.ruleKey(), details));
    }

    public record Command(String ruleKey, String name, boolean enabled, AutomationRule.Trigger trigger,
                          List<AutomationRule.Condition> conditions, List<AutomationRule.Action> actions) {}
}
