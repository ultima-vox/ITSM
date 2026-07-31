package ru.ultimavox.itsm.platform.localization;

import java.time.Duration;
import java.util.Set;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.platform.cache.CachePort;

/**
 * Application boundary for user locale preference. Storage is JDBC via
 * {@link LocalePreferenceRepository}; hot path reads go through {@link CachePort}
 * (Redis under compose profile, concurrent-map otherwise).
 */
@Service
public class LocalePreferenceService {

  private static final String DEFAULT_LOCALE = "ru";
  private static final Set<String> SUPPORTED = Set.of("ru", "en", "de");
  private static final Duration CACHE_TTL = Duration.ofMinutes(15);
  private static final String CACHE_PREFIX = "locale:";

  private final LocalePreferenceRepository repository;
  private final CachePort cache;

  public LocalePreferenceService(LocalePreferenceRepository repository, CachePort cache) {
    this.repository = repository;
    this.cache = cache;
  }

  public Set<String> supportedLocales() {
    return SUPPORTED;
  }

  public String preferenceFor(String subject) {
    String key = CACHE_PREFIX + subject;
    return cache.get(key, String.class).orElseGet(() -> {
      String locale = repository.findLocale(subject).orElse(DEFAULT_LOCALE);
      cache.put(key, locale, CACHE_TTL);
      return locale;
    });
  }

  public String changePreference(String subject, String locale) {
    if (!SUPPORTED.contains(locale)) {
      throw new IllegalArgumentException("Unsupported interface locale");
    }
    repository.upsert(subject, locale);
    cache.put(CACHE_PREFIX + subject, locale, CACHE_TTL);
    return locale;
  }
}
