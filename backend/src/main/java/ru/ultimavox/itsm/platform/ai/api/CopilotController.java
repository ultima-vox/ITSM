package ru.ultimavox.itsm.platform.ai.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.ultimavox.itsm.platform.ai.AiGateway;
import ru.ultimavox.itsm.platform.ai.PolicyGate;

@RestController
@RequestMapping("/api/v1/ai/copilot")
@Tag(name = "AI Copilot")
class CopilotController {
  private final AiGateway gateway;
  private final PolicyGate policyGate;

  CopilotController(AiGateway gateway, PolicyGate policyGate) {
    this.gateway = gateway;
    this.policyGate = policyGate;
  }

  @PostMapping("/summarize")
  @Operation(summary = "Summarize free-form operational text (advisory only)")
  AiGateway.Suggestion summarize(Authentication authentication, @Valid @RequestBody PromptRequest body) {
    try {
      var prompt = policyGate.authorize(
          authentication.getName(),
          "ai.summarize",
          "summarize",
          body.content(),
          body.maxTokens(),
          body.scopes()
      );
      return gateway.summarize(prompt);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  @PostMapping("/suggest")
  @Operation(summary = "Suggest a resolution path (advisory only; never mutates domain)")
  AiGateway.Suggestion suggest(Authentication authentication, @Valid @RequestBody PromptRequest body) {
    try {
      var prompt = policyGate.authorize(
          authentication.getName(),
          "ai.suggest",
          "suggest-resolution",
          body.content(),
          body.maxTokens(),
          body.scopes()
      );
      return gateway.suggestResolution(prompt);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  record PromptRequest(
      @NotBlank @Size(max = 50000) String content,
      @Min(1) @Max(4096) Integer maxTokens,
      Set<String> scopes
  ) {}
}
