package ru.ultimavox.itsm.platform.localization;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

@Repository
class JdbcLocalePreferenceRepository implements LocalePreferenceRepository {

  private final JdbcTemplate jdbc;

  JdbcLocalePreferenceRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<String> findLocale(String subjectId) {
    List<String> rows = jdbc.query(
        "SELECT locale FROM user_locale_preference WHERE org_id = ? AND subject_id = ?",
        (rs, i) -> rs.getString("locale"),
        OrganizationContext.current(), subjectId
    );
    return rows.stream().findFirst();
  }

  @Override
  public void upsert(String subjectId, String locale) {
    jdbc.update(
        """
        INSERT INTO user_locale_preference (org_id, subject_id, locale, updated_at)
        VALUES (?, ?, ?, now())
        ON CONFLICT (org_id, subject_id) DO UPDATE
          SET locale = EXCLUDED.locale,
              updated_at = now()
        """,
        OrganizationContext.current(), subjectId,
        locale
    );
  }
}
