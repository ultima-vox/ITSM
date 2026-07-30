package ru.ultimavox.itsm.platform.localization;
import java.util.*; import java.util.concurrent.ConcurrentHashMap; import org.springframework.stereotype.Service;
/** Application boundary for user locale preference. Replace storage adapter without changing API callers. */
@Service public class LocalePreferenceService {
 private final Map<String,String> preferences = new ConcurrentHashMap<>();
 private final Set<String> supported = Set.of("ru", "en", "de");
 public Set<String> supportedLocales() { return supported; }
 public String preferenceFor(String subject) { return preferences.getOrDefault(subject, "ru"); }
 public String changePreference(String subject, String locale) { if (!supported.contains(locale)) throw new IllegalArgumentException("Unsupported interface locale"); preferences.put(subject, locale); return locale; }
}
