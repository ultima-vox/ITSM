package ru.ultimavox.itsm.platform.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ultimavox.itsm.platform.authorization.SelfScopedEndpoint;

@RestController
@SelfScopedEndpoint
final class GuardedProbeController {
  @GetMapping("/api/v1/security-probe")
  String probe() {
    return "ok";
  }
}
