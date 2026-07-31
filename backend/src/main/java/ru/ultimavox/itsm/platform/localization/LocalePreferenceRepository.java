package ru.ultimavox.itsm.platform.localization;

import java.util.Optional;

/** Persistence port for per-subject UI locale preference. */
public interface LocalePreferenceRepository {

  Optional<String> findLocale(String subjectId);

  void upsert(String subjectId, String locale);
}
