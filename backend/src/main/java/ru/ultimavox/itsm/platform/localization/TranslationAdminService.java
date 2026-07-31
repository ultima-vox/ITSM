package ru.ultimavox.itsm.platform.localization;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Admin CRUD for rows in the {@code translation} table (V1 foundation). */
@Service
public class TranslationAdminService {

  private final JdbcTemplate jdbc;

  public TranslationAdminService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<TranslationEntry> list(String namespace, String locale) {
    StringBuilder sql = new StringBuilder(
        """
        SELECT id, namespace, translation_key, locale, value, version, updated_at
        FROM translation
        WHERE 1=1
        """
    );
    List<Object> args = new ArrayList<>();
    if (namespace != null && !namespace.isBlank()) {
      sql.append(" AND namespace = ?");
      args.add(namespace);
    }
    if (locale != null && !locale.isBlank()) {
      sql.append(" AND locale = ?");
      args.add(locale);
    }
    sql.append(" ORDER BY namespace, translation_key, locale");

    return jdbc.query(
        sql.toString(),
        (rs, i) -> new TranslationEntry(
            rs.getObject("id", UUID.class),
            rs.getString("namespace"),
            rs.getString("translation_key"),
            rs.getString("locale"),
            rs.getString("value"),
            rs.getInt("version"),
            rs.getTimestamp("updated_at").toInstant()
        ),
        args.toArray()
    );
  }

  public TranslationEntry upsert(String namespace, String translationKey, String locale, String value) {
    if (namespace == null || namespace.isBlank()) {
      throw new IllegalArgumentException("namespace is required");
    }
    if (translationKey == null || translationKey.isBlank()) {
      throw new IllegalArgumentException("key is required");
    }
    if (locale == null || locale.isBlank()) {
      throw new IllegalArgumentException("locale is required");
    }
    if (value == null) {
      throw new IllegalArgumentException("value is required");
    }

    jdbc.update(
        """
        INSERT INTO translation (namespace, translation_key, locale, value, version, updated_at)
        VALUES (?, ?, ?, ?, 1, now())
        ON CONFLICT (namespace, translation_key, locale) DO UPDATE
          SET value = EXCLUDED.value,
              version = translation.version + 1,
              updated_at = now()
        """,
        namespace,
        translationKey,
        locale,
        value
    );

    List<TranslationEntry> rows = jdbc.query(
        """
        SELECT id, namespace, translation_key, locale, value, version, updated_at
        FROM translation
        WHERE namespace = ? AND translation_key = ? AND locale = ?
        """,
        (rs, i) -> new TranslationEntry(
            rs.getObject("id", UUID.class),
            rs.getString("namespace"),
            rs.getString("translation_key"),
            rs.getString("locale"),
            rs.getString("value"),
            rs.getInt("version"),
            rs.getTimestamp("updated_at").toInstant()
        ),
        namespace,
        translationKey,
        locale
    );
    return rows.stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Upsert did not persist translation"));
  }

  public record TranslationEntry(
      UUID id,
      String namespace,
      String key,
      String locale,
      String value,
      int version,
      Instant updatedAt
  ) {}
}
