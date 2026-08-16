package ru.ultimavox.itsm.platform.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI copilot provider settings. When {@code ollama.url} is blank the advisory
 * logging gateway remains active; set it to an OpenAI-compatible endpoint
 * (e.g. {@code http://localhost:11434/v1}) to enable a real model provider.
 */
@ConfigurationProperties(prefix = "itsm.ai")
public class ItsmAiProperties {

  private final Ollama ollama = new Ollama();

  public Ollama getOllama() {
    return ollama;
  }

  public boolean isConfigured() {
    return ollama.url != null && !ollama.url.isBlank();
  }

  public static class Ollama {

    /** OpenAI-compatible base URL, e.g. {@code http://localhost:11434/v1}. Empty keeps the logging gateway. */
    private String url = "";

    /** Model id served by the endpoint. */
    private String model = "";

    private Duration connectTimeout = Duration.ofSeconds(2);

    private Duration readTimeout = Duration.ofSeconds(120);

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }

    public Duration getConnectTimeout() {
      return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
      this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
      return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
      this.readTimeout = readTimeout;
    }
  }
}
