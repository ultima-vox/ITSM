package ru.ultimavox.itsm.platform.automation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.automation.AutomationRule;
import ru.ultimavox.itsm.platform.automation.AutomationRuleRepository;

@RestController
@RequestMapping("/api/v1/automation")
@Tag(name = "Platform — Automation")
class AutomationAdminController {

  private final AutomationRuleRepository rules;
  private final AccessControl access;

  AutomationAdminController(AutomationRuleRepository rules, AccessControl access) {
    this.rules = rules;
    this.access = access;
  }

  @GetMapping("/rules")
  @Operation(summary = "List automation rules (admin read)")
  List<RuleResponse> listRules(Authentication authentication) {
    String actor = authentication != null ? authentication.getName() : null;
    access.require(actor, "automation.read", "automation_rule", null);
    return rules.listAll().stream().map(RuleResponse::from).toList();
  }

  record RuleResponse(
      UUID id,
      String key,
      String name,
      boolean enabled,
      String eventType,
      List<ConditionResponse> conditions,
      List<ActionResponse> actions
  ) {
    static RuleResponse from(AutomationRule r) {
      return new RuleResponse(
          r.id(),
          r.ruleKey(),
          r.name(),
          r.enabled(),
          r.trigger() == null ? null : r.trigger().eventType(),
          r.conditions().stream()
              .map(c -> new ConditionResponse(
                  c.field(),
                  c.operator() == null ? null : c.operator().name(),
                  c.value()
              ))
              .toList(),
          r.actions().stream()
              .map(a -> new ActionResponse(a.type(), a.parameters()))
              .toList()
      );
    }
  }

  record ConditionResponse(String field, String operator, String value) {}

  record ActionResponse(String type, Map<String, Object> parameters) {}
}
