package ru.ultimavox.itsm.platform.users;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcUserProfileRepository implements UserProfileRepository {
  private final JdbcTemplate jdbc;

  JdbcUserProfileRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Map<String, UserProfile> findBySubjectIds(Collection<String> subjectIds, String orgId) {
    if (subjectIds == null || subjectIds.isEmpty()) {
      return Map.of();
    }
    List<String> unique = List.copyOf(new java.util.LinkedHashSet<>(subjectIds));
    Map<String, UserProfile> result = new HashMap<>();
    jdbc.query(
        "SELECT subject_id, username, display_name, email, avatar_url FROM user_profile WHERE org_id = ? AND subject_id = ANY(?)",
        rs -> {
          result.put(rs.getString("subject_id"), new UserProfile(
              rs.getString("subject_id"),
              rs.getString("username"),
              rs.getString("display_name"),
              rs.getString("email"),
              rs.getString("avatar_url")
          ));
        },
        orgId, unique.toArray(new String[0])
    );
    return result;
  }

  @Override
  public void upsert(UserProfile profile, String orgId) {
    Timestamp now = Timestamp.from(Instant.now());
    jdbc.update("""
        INSERT INTO user_profile (subject_id, org_id, username, display_name, email, avatar_url, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (org_id, subject_id) DO UPDATE SET
          username = EXCLUDED.username, display_name = EXCLUDED.display_name,
          email = EXCLUDED.email, avatar_url = EXCLUDED.avatar_url, updated_at = EXCLUDED.updated_at
        """,
        profile.subjectId(), orgId, profile.username(), profile.displayName(),
        profile.email(), profile.avatarUrl(), now, now
    );
  }
}
