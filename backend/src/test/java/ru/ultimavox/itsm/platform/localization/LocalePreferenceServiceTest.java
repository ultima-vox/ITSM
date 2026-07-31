package ru.ultimavox.itsm.platform.localization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.ultimavox.itsm.platform.cache.ConcurrentMapCachePort;

class LocalePreferenceServiceTest {

  private LocalePreferenceService service;
  private ConcurrentMap<String, String> store;

  @BeforeEach
  void setUp() {
    store = new ConcurrentHashMap<>();
    LocalePreferenceRepository repo = new LocalePreferenceRepository() {
      @Override
      public Optional<String> findLocale(String subjectId) {
        return Optional.ofNullable(store.get(subjectId));
      }

      @Override
      public void upsert(String subjectId, String locale) {
        store.put(subjectId, locale);
      }
    };
    service = new LocalePreferenceService(repo, new ConcurrentMapCachePort());
  }

  @Test
  void defaults_to_ru_when_no_preference() {
    assertThat(service.preferenceFor("user-1")).isEqualTo("ru");
  }

  @Test
  void supported_locales_are_ru_en_de() {
    assertThat(service.supportedLocales()).containsExactlyInAnyOrder("ru", "en", "de");
  }

  @Test
  void changePreference_persists_and_returns_locale() {
    assertThat(service.changePreference("user-1", "en")).isEqualTo("en");
    assertThat(service.preferenceFor("user-1")).isEqualTo("en");
    assertThat(store).containsEntry("user-1", "en");
  }

  @Test
  void changePreference_rejects_unsupported_locale() {
    assertThatThrownBy(() -> service.changePreference("user-1", "fr"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported");
    assertThat(store).doesNotContainKey("user-1");
  }

  @Test
  void changePreference_overwrites_existing() {
    service.changePreference("user-1", "en");
    service.changePreference("user-1", "de");
    assertThat(service.preferenceFor("user-1")).isEqualTo("de");
  }
}
