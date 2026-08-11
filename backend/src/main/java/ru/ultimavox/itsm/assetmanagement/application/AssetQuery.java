package ru.ultimavox.itsm.assetmanagement.application;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.assetmanagement.domain.Asset;

@Service
public class AssetQuery {
  private final JdbcTemplate jdbc;

  public AssetQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Asset> list(String status, String kind, String owner) {
    String statusFilter = blankToNull(status);
    String kindFilter = blankToNull(kind);
    String ownerFilter = blankToNull(owner);
    return jdbc.query(
        """
            SELECT id, asset_tag, kind, status, owner_subject, configuration_item_id, acquired_on, warranty_until, version
            FROM asset
            WHERE org_id = ?
              AND (?::text IS NULL OR status = ?)
              AND (?::text IS NULL OR kind = ?)
              AND (?::text IS NULL OR owner_subject = ?)
            ORDER BY asset_tag
            """,
        (rs, i) -> map(rs.getObject("id", UUID.class),
            rs.getString("asset_tag"),
            rs.getString("kind"),
            rs.getString("status"),
            rs.getString("owner_subject"),
            rs.getObject("configuration_item_id", UUID.class),
            rs.getDate("acquired_on"),
            rs.getDate("warranty_until"), rs.getLong("version")),
        OrganizationContext.current(), statusFilter, statusFilter, kindFilter, kindFilter, ownerFilter, ownerFilter
    );
  }

  public Optional<Asset> findById(UUID id) {
    List<Asset> rows = jdbc.query(
        """
            SELECT id, asset_tag, kind, status, owner_subject, configuration_item_id, acquired_on, warranty_until, version
            FROM asset WHERE id = ? AND org_id = ?
            """,
        (rs, i) -> map(rs.getObject("id", UUID.class),
            rs.getString("asset_tag"),
            rs.getString("kind"),
            rs.getString("status"),
            rs.getString("owner_subject"),
            rs.getObject("configuration_item_id", UUID.class),
            rs.getDate("acquired_on"),
            rs.getDate("warranty_until"), rs.getLong("version")),
        id, OrganizationContext.current()
    );
    return rows.stream().findFirst();
  }

  private static Asset map(
      UUID id,
      String tag,
      String kind,
      String status,
      String owner,
      UUID ciId,
      Date acquired,
      Date warranty,
      long version
  ) {
    return new Asset(
        id,
        tag,
        Asset.Kind.valueOf(kind),
        Asset.Status.valueOf(status),
        owner,
        ciId,
        toLocalDate(acquired),
        toLocalDate(warranty),
        version
    );
  }

  private static LocalDate toLocalDate(Date date) {
    return date == null ? null : date.toLocalDate();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
