package ru.ultimavox.itsm.platform.ai;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Provider-neutral, policy-gated advisory interface.
 * AI adapters must never write domain tables; they only return suggestions for human review.
 */
public interface AiGateway {
  Suggestion summarize(AuthorizedPrompt prompt);

  Suggestion suggestResolution(AuthorizedPrompt prompt);

  Suggestion draftReply(AuthorizedPrompt prompt);

  record AuthorizedPrompt(
      String subject,
      Set<String> scopes,
      String task,
      String content,
      UUID correlationId,
      Integer maxTokens
  ) {}

  record Suggestion(
      String provider,
      String model,
      String content,
      List<String> citations,
      boolean requiresHumanReview
  ) {}
}
