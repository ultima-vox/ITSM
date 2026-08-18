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

  public List<Asset> list(String status, String kind, String owner, String q) {
    String statusFilter = blankToNull(status);
    String kindFilter = blankToNull(kind);
    String ownerFilter = blankToNull(owner);
    String queryFilter = blankToNull(q);
    StringBuilder sql = new StringBuilder(
        """
            SELECT id, asset_tag, name, kind, status, owner_subject, configuration_item_id,
                   acquired_on, warranty_until, location, version
            FROM asset
            WHERE org_id = ?
              AND (?::text IS NULL OR status = ?)
              AND (?::text IS NULL OR kind = ?)
              AND (?::text IS NULL OR owner_subject = ?)
        """);
    java.util.List<Object> args = new java.util.ArrayList<>();
    args.add(OrganizationContext.current());
    args.add(statusFilter); args.add(statusFilter);
    args.add(kindFilter); args.add(kindFilter);
    args.add(ownerFilter); args.add(ownerFilter);
    if (queryFilter != null) {
        String pattern = "%" + queryFilter.toLowerCase() + "%";
        sql.append(" AND (lower(asset_tag) LIKE ? OR lower(name) LIKE ? OR lower(location) LIKE ? OR lower(owner_subject) LIKE ?)");
        args.add(pattern); args.add(pattern); args.add(pattern); args.add(pattern);
    }
    sql.append(" ORDER BY asset_tag");
    return jdbc.query(sql.toString(),
        (rs, i) -> map(rs),
        args.toArray()
    );
  }

  public Optional<Asset> findById(UUID id) {
    List<Asset> rows = jdbc.query(
        """
            SELECT id, asset_tag, name, kind, status, owner_subject, configuration_item_id,
                   acquired_on, warranty_until, location, version
            FROM asset WHERE id = ? AND org_id = ?
            """,
        (rs, i) -> map(rs),
        id, OrganizationContext.current()
    );
    return rows.stream().findFirst();
  }

  private static Asset map(java.sql.ResultSet rs) throws java.sql.SQLException {
    UUID id = rs.getObject("id", UUID.class);
    String tag = rs.getString("asset_tag");
    String name = rs.getString("name");
    String kind = rs.getString("kind");
    String status = rs.getString("status");
    String owner = rs.getString("owner_subject");
    UUID ciId = rs.getObject("configuration_item_id", UUID.class);
    java.sql.Date acquired = rs.getDate("acquired_on");
    java.sql.Date warranty = rs.getDate("warranty_until");
    String location = rs.getString("location");
    long version = rs.getLong("version");
    return new Asset(id, tag, name, Asset.Kind.valueOf(kind), Asset.Status.valueOf(status),
        owner, ciId, toLocalDate(acquired), toLocalDate(warranty), location, version);
  }

  private static LocalDate toLocalDate(Date date) {
    return date == null ? null : date.toLocalDate();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
