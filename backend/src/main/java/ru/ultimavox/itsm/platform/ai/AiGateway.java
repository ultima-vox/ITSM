package ru.ultimavox.itsm.platform.ai;
import java.util.*;
/** Provider-neutral, policy-gated advisory interface. AI adapters cannot directly mutate domain aggregates. */
public interface AiGateway { Suggestion suggest(AuthorizedPrompt prompt); record AuthorizedPrompt(String subject, Set<String> scopes, String task, String content, UUID correlationId) {} record Suggestion(String provider, String model, String content, List<String> citations, boolean requiresHumanReview) {} }
