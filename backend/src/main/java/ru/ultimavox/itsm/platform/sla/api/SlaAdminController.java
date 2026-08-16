package ru.ultimavox.itsm.platform.sla.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.sla.SlaPolicy;
import ru.ultimavox.itsm.platform.sla.SlaPolicyRepository;
import ru.ultimavox.itsm.platform.sla.SlaPolicyRepository.SlaPolicyView;

@RestController
@RequestMapping("/api/v1/sla")
@Tag(name = "Platform — SLA")
class SlaAdminController {

  private final SlaPolicyRepository policies;
  private final AccessControl access;

  SlaAdminController(SlaPolicyRepository policies, AccessControl access) {
    this.policies = policies;
    this.access = access;
  }

  @GetMapping("/policies")
  @Operation(summary = "List SLA policies (admin read)")
  List<PolicyResponse> listPolicies(Authentication authentication) {
    String actor = authentication != null ? authentication.getName() : null;
    access.require(actor, "sla.read", "sla_policy", null);
    return policies.listAll().stream().map(PolicyResponse::from).toList();
  }

  record PolicyResponse(
      UUID id,
      String key,
      String calendarKey,
      boolean enabled,
      int version,
      List<TargetResponse> targets,
      List<String> pauseStates
  ) {
    static PolicyResponse from(SlaPolicyView view) {
      SlaPolicy p = view.policy();
      return new PolicyResponse(
          p.id(),
          p.key(),
          p.calendarKey(),
          view.enabled(),
          view.version(),
          p.targets().stream()
              .map(t -> new TargetResponse(
                  t.metric(),
                  t.condition(),
                  t.target() == null ? 0L : t.target().toMinutes(),
                  t.warningBefore() == null ? 0L : t.warningBefore().toMinutes()
              ))
              .toList(),
          List.copyOf(p.pauseStates())
      );
    }
  }

  record TargetResponse(
      String metric,
      String condition,
      long targetMinutes,
      long warningBeforeMinutes
  ) {}
}
