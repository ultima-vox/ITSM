package ru.ultimavox.itsm.platform.automation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.automation.AutomationRule;
import ru.ultimavox.itsm.platform.automation.AutomationRuleAdminService;
import ru.ultimavox.itsm.platform.automation.AutomationRuleRepository;

@RestController
@RequestMapping("/api/v1/automation")
@Tag(name = "Platform — Automation")
class AutomationAdminController {

  private final AutomationRuleRepository rules;
  private final AccessControl access;
  private final AutomationRuleAdminService admin;

  AutomationAdminController(AutomationRuleRepository rules, AutomationRuleAdminService admin, AccessControl access) {
    this.rules = rules;
    this.admin = admin;
    this.access = access;
  }

  @GetMapping("/rules")
  @Operation(summary = "List automation rules (admin read)")
  List<RuleResponse> listRules(Authentication authentication) {
    String actor = authentication != null ? authentication.getName() : null;
    access.require(actor, "automation.read", "automation_rule", null);
    return rules.listAll().stream().map(RuleResponse::from).toList();
  }

  @PatchMapping("/rules/{id}")
  @Operation(summary = "Enable or disable an automation rule for current organization")
  RuleResponse setEnabled(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody SetEnabledRequest body
  ) {
    String actor = authentication != null ? authentication.getName() : null;
    access.require(actor, "automation.write", "automation_rule", id.toString());
    return RuleResponse.from(admin.setEnabled(actor, id, body.enabled()));
  }

  @PostMapping("/rules")
  @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  RuleResponse create(Authentication authentication, @RequestBody AutomationRuleAdminService.Command body) {
    String actor = authentication != null ? authentication.getName() : null;
    access.require(actor, "automation.write", "automation_rule", body.ruleKey());
    return RuleResponse.from(admin.create(actor, body));
  }

  @PutMapping("/rules/{id}")
  RuleResponse update(Authentication authentication, @PathVariable UUID id,
                      @org.springframework.web.bind.annotation.RequestParam int expectedVersion,
                      @RequestBody AutomationRuleAdminService.Command body) {
    String actor = authentication != null ? authentication.getName() : null;
    access.require(actor, "automation.write", "automation_rule", id.toString());
    return RuleResponse.from(admin.update(actor, id, expectedVersion, body));
  }

  record SetEnabledRequest(boolean enabled) {}

  record RuleResponse(
      UUID id,
      String key,
      String name,
      int version,
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
          r.version(),
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
