package ru.ultimavox.itsm.platform.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Enables {@link ItsmAiProperties}; selects Ollama or logging gateway via conditions. */
@Configuration
@EnableConfigurationProperties(ItsmAiProperties.class)
class AiConfiguration {
}
