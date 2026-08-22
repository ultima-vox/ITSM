package ru.ultimavox.itsm.platform.identity;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

@Service
public class IdentitySyncService {
  private final JdbcTemplate jdbc;

  public IdentitySyncService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public void sync(Jwt jwt) {
    if (jwt == null) {
      throw new InvalidBearerTokenException("Access token is required");
    }
    String subject = jwt.getSubject();
    if (subject == null || subject.isBlank()) {
      throw new InvalidBearerTokenException(
          "Access token has no 'sub' claim; the identity provider client must include the "
              + "'basic' client scope");
    }
    if (jwt.getIssuer() == null) {
      throw new InvalidBearerTokenException("Access token has no 'iss' claim");
    }
    String idp = jwt.getIssuer().toString();
    Boolean enabled = jdbc.queryForObject(
        """
            INSERT INTO identity_account (id, idp, external_id, subject_id, enabled, last_sync)
            VALUES (?, ?, ?, ?, TRUE, now())
            ON CONFLICT (idp, external_id) DO UPDATE SET
              subject_id = EXCLUDED.subject_id,
              last_sync = now()
            RETURNING enabled
            """,
        Boolean.class,
        UUID.randomUUID(), idp, subject, subject
    );
    if (enabled == null || !enabled) {
      throw new DisabledException("Identity account is disabled");
    }
    Set<String> tokens = claimTokens(jwt);
    if (tokens.isEmpty()) {
      return;
    }
    List<String> roleKeys = jdbc.query(
        """
            SELECT DISTINCT role_name
            FROM group_role_mapping
            WHERE idp_group = ANY(?)
            """,
        (rs, i) -> rs.getString(1),
        (Object) tokens.toArray(String[]::new)
    );
    if (roleKeys.isEmpty()) {
      return;
    }
    jdbc.update(
        """
            INSERT INTO principal_role (org_id, subject_id, role_id)
            SELECT ?, ?, r.id
            FROM role r
            WHERE r.role_key = ANY(?)
            ON CONFLICT (org_id, subject_id, role_id) DO NOTHING
            """,
        OrganizationContext.current(), subject, roleKeys.toArray(String[]::new)
    );
  }

  static Set<String> claimTokens(Jwt jwt) {
    Set<String> tokens = new LinkedHashSet<>();
    addClaimValues(tokens, jwt.getClaim("groups"));
    Map<String, Object> realm = jwt.getClaimAsMap("realm_access");
    if (realm != null) {
      addClaimValues(tokens, realm.get("roles"));
    }
    return tokens;
  }

  private static void addClaimValues(Set<String> tokens, Object claim) {
    if (claim instanceof Collection<?> values) {
      for (Object value : values) {
        if (value instanceof String text) {
          addGroupToken(tokens, text);
        }
      }
      return;
    }
    if (claim instanceof String raw) {
      for (String part : raw.split("[,\\s]+")) {
        addGroupToken(tokens, part);
      }
    }
  }

  private static void addGroupToken(Set<String> tokens, String raw) {
    if (raw == null) {
      return;
    }
    String value = raw.trim();
    if (value.isEmpty()) {
      return;
    }
    tokens.add(value);
    String noSlash = value.startsWith("/") ? value.substring(1) : value;
    if (noSlash.isEmpty()) {
      return;
    }
    tokens.add(noSlash);
    int slash = noSlash.lastIndexOf('/');
    if (slash >= 0 && slash < noSlash.length() - 1) {
      tokens.add(noSlash.substring(slash + 1));
    }
  }
}
