package ru.ultimavox.itsm.platform.localization;
import java.util.Locale; import java.util.Map;
/** Locale lookup with a declared fallback. Translation administration persists these values as metadata. */
public record LocalizedText(String fallback, Map<String,String> values) { public String forLocale(Locale locale) { return values.getOrDefault(locale.toLanguageTag(), values.getOrDefault(locale.getLanguage(), fallback)); } }
