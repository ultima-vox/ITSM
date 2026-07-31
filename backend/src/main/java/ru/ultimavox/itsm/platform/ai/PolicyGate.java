package ru.ultimavox.itsm.platform.ai;

import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import ru.ultimavox.itsm.platform.authorization.AccessControl;

/**
 * Enforces permission and token budgets before any AI provider is invoked.
 * Never mutates domain data.
 */
@Component
public class PolicyGate {
  public static final int DEFAULT_MAX_TOKENS = 1024;
  public static final int HARD_MAX_TOKENS = 4096;

  private final AccessControl access;

  public PolicyGate(AccessControl access) {
    this.access = access;
  }

  public AiGateway.AuthorizedPrompt authorize(
      String subject,
      String permission,
      String task,
      String content,
      Integer requestedMaxTokens,
      Set<String> scopes
  ) {
    access.require(subject, permission, "ai-copilot", task);
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("Prompt content is required");
    }
    if (content.length() > 50_000) {
      throw new IllegalArgumentException("Prompt content exceeds size limit");
    }
    int maxTokens = requestedMaxTokens == null ? DEFAULT_MAX_TOKENS : requestedMaxTokens;
    if (maxTokens < 1 || maxTokens > HARD_MAX_TOKENS) {
      throw new IllegalArgumentException("maxTokens must be between 1 and " + HARD_MAX_TOKENS);
    }
    Set<String> effectiveScopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    if (effectiveScopes.isEmpty()) {
      // scopes may be empty when role-based permission already passed AccessControl
    }
    return new AiGateway.AuthorizedPrompt(
        subject,
        effectiveScopes,
        task,
        content,
        java.util.UUID.randomUUID(),
        maxTokens
    );
  }

  public void refuseIfMissingPermission(String subject, String permission) {
    try {
      access.require(subject, permission, "ai-copilot", null);
    } catch (AccessDeniedException ex) {
      throw new AccessDeniedException("AI copilot refused: missing permission " + permission);
    }
  }
}
