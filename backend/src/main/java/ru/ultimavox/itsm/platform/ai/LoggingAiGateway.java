package ru.ultimavox.itsm.platform.ai;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Deterministic advisory fallback used when no real AI provider is configured
 * ({@code itsm.ai.ollama.url} unset). Logs prompt metadata only (not full content
 * by default) and never writes domain tables.
 */
@Component
@Conditional(OllamaDisabledCondition.class)
public class LoggingAiGateway implements AiGateway {
  private static final Logger log = LoggerFactory.getLogger(LoggingAiGateway.class);
  private static final String PROVIDER = "logging-stub";
  private static final String MODEL = "vox-local-advisory-v1";

  @Override
  public Suggestion summarize(AuthorizedPrompt prompt) {
    logInvocation("summarize", prompt);
    String summary = truncate(prompt.content(), Math.min(480, prompt.maxTokens() * 4));
    return new Suggestion(
        PROVIDER,
        MODEL,
        "Summary (requires human review):\n" + summary,
        List.of(),
        true
    );
  }

  @Override
  public Suggestion suggestResolution(AuthorizedPrompt prompt) {
    logInvocation("suggestResolution", prompt);
    return new Suggestion(
        PROVIDER,
        MODEL,
        """
            Suggested resolution steps (requires human review):
            1. Confirm the reported symptom and affected service.
            2. Check recent changes and related CI health.
            3. Apply the known workaround if safe.
            4. Capture root cause notes for problem management.
            Context excerpt: %s
            """.formatted(truncate(prompt.content(), 240)).strip(),
        List.of("internal:playbook-generic"),
        true
    );
  }

  @Override
  public Suggestion draftReply(AuthorizedPrompt prompt) {
    logInvocation("draftReply", prompt);
    return new Suggestion(
        PROVIDER,
        MODEL,
        """
            Draft reply (requires human review):

            Здравствуйте!

            Мы получили ваше обращение и уже разбираемся с ситуацией.
            Кратко по сути: %s

            Мы сообщим, как только появится обновление.

            С уважением,
            Служба поддержки
            """.formatted(truncate(prompt.content(), 200)).strip(),
        List.of(),
        true
    );
  }

  private static void logInvocation(String operation, AuthorizedPrompt prompt) {
    log.info(
        "ai.gateway op={} subject={} task={} correlationId={} maxTokens={} contentChars={}",
        operation,
        prompt.subject(),
        prompt.task(),
        prompt.correlationId(),
        prompt.maxTokens(),
        prompt.content() == null ? 0 : prompt.content().length()
    );
  }

  private static String truncate(String value, int max) {
    if (value == null) {
      return "";
    }
    String normalized = value.replaceAll("\\s+", " ").trim();
    if (normalized.length() <= max) {
      return normalized;
    }
    return normalized.substring(0, Math.max(0, max - 1)) + "…";
  }
}
