package ru.ultimavox.itsm.platform.localization;
import static org.assertj.core.api.Assertions.assertThat; import java.util.*; import org.junit.jupiter.api.Test;
class LocalizedTextTest { @Test void uses_exact_locale_then_language_then_fallback() { var text=new LocalizedText("Fallback",Map.of("ru","Русский","en","English","en-GB","British English")); assertThat(text.forLocale(Locale.forLanguageTag("en-GB"))).isEqualTo("British English"); assertThat(text.forLocale(Locale.CANADA)).isEqualTo("English"); assertThat(text.forLocale(Locale.JAPANESE)).isEqualTo("Fallback"); } }
