package ru.ultimavox.itsm.servicecatalog.application;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.servicecatalog.domain.CatalogItem;

@Service
public class CatalogQuery {
  private final JdbcTemplate jdbc;

  public CatalogQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<CatalogItemView> search(String category, String query, String locale) {
    String loc = locale == null || locale.isBlank() ? "ru" : locale;
    String sql = """
        SELECT ci.id, ci.item_key, ci.status, ci.form_definition_id, ci.workflow_definition_id,
               t.locale, t.name, t.description, t.category
        FROM catalog_item ci
        JOIN catalog_item_translation t ON t.catalog_item_id = ci.id
        WHERE ci.org_id = ?
          AND ci.status = 'PUBLISHED'
          AND t.locale = ?
          AND (?::text IS NULL OR t.category = ?)
          AND (?::text IS NULL OR t.name ILIKE '%' || ? || '%' OR t.description ILIKE '%' || ? || '%' OR ci.item_key ILIKE '%' || ? || '%')
        ORDER BY t.category NULLS LAST, t.name
        """;
    String q = blankToNull(query);
    String cat = blankToNull(category);
    return jdbc.query(
        sql,
        (rs, i) -> new CatalogItemView(
            (UUID) rs.getObject("id"),
            rs.getString("item_key"),
            rs.getString("status"),
            (UUID) rs.getObject("form_definition_id"),
            (UUID) rs.getObject("workflow_definition_id"),
            rs.getString("locale"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("category")
        ),
        OrganizationContext.current(), loc, cat, cat, q, q, q, q
    );
  }

  public Optional<CatalogItemDetail> findById(UUID id, String locale) {
    String loc = locale == null || locale.isBlank() ? "ru" : locale;
    List<CatalogItemDetail> rows = jdbc.query(
        """
            SELECT ci.id, ci.item_key, ci.status, ci.form_definition_id, ci.workflow_definition_id,
                   ci.eligibility_rules::text AS eligibility_rules,
                   t.locale, t.name, t.description, t.category
            FROM catalog_item ci
            LEFT JOIN catalog_item_translation t ON t.catalog_item_id = ci.id
            WHERE ci.id = ? AND ci.org_id = ?
            ORDER BY CASE WHEN t.locale = ? THEN 0 ELSE 1 END, t.locale
            """,
        (rs, i) -> new CatalogItemDetail(
            (UUID) rs.getObject("id"),
            rs.getString("item_key"),
            rs.getString("status"),
            (UUID) rs.getObject("form_definition_id"),
            (UUID) rs.getObject("workflow_definition_id"),
            rs.getString("eligibility_rules"),
            rs.getString("locale"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("category")
        ),
        id, OrganizationContext.current(), loc
    );
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    CatalogItemDetail primary = rows.getFirst();
    List<BundleComponent> components = jdbc.query("""
        SELECT ci.id, ci.item_key, bc.quantity, bc.position
        FROM catalog_bundle_component bc JOIN catalog_item ci ON ci.id=bc.component_item_id
        WHERE bc.bundle_item_id=? AND bc.org_id=? AND ci.org_id=? AND ci.status='PUBLISHED'
        ORDER BY bc.position, ci.item_key
        """, (rs, row) -> new BundleComponent(rs.getObject("id", UUID.class), rs.getString("item_key"),
        rs.getInt("quantity"), rs.getInt("position")), id, OrganizationContext.current(), OrganizationContext.current());
    Map<String, CatalogItem.Translation> translations = new LinkedHashMap<>();
    for (CatalogItemDetail row : rows) {
      if (row.locale() != null) {
        translations.put(row.locale(), new CatalogItem.Translation(row.name(), row.description(), row.category()));
      }
    }
    return Optional.of(new CatalogItemDetail(
        primary.id(),
        primary.key(),
        primary.status(),
        primary.formDefinitionId(),
        primary.workflowDefinitionId(),
        primary.eligibilityRulesJson(),
        primary.locale(),
        primary.name(),
        primary.description(),
        primary.category(),
        translations,
        components
    ));
  }

  public Optional<CatalogItem> findDomainPublished(UUID id) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        """
            SELECT ci.id, ci.item_key, ci.status, ci.form_definition_id, ci.workflow_definition_id,
                   t.locale, t.name, t.description, t.category
            FROM catalog_item ci
            LEFT JOIN catalog_item_translation t ON t.catalog_item_id = ci.id
            WHERE ci.id = ? AND ci.org_id = ?
            """,
        id, OrganizationContext.current()
    );
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    Map<String, Object> head = rows.getFirst();
    Map<String, CatalogItem.Translation> translations = new HashMap<>();
    for (Map<String, Object> row : rows) {
      if (row.get("locale") != null) {
        translations.put(
            (String) row.get("locale"),
            new CatalogItem.Translation(
                (String) row.get("name"),
                (String) row.get("description"),
                (String) row.get("category")
            )
        );
      }
    }
    return Optional.of(new CatalogItem(
        (UUID) head.get("id"),
        (String) head.get("item_key"),
        CatalogItem.Status.valueOf((String) head.get("status")),
        (UUID) head.get("form_definition_id"),
        (UUID) head.get("workflow_definition_id"),
        translations,
        List.of()
    ));
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  public record CatalogItemView(
      UUID id,
      String key,
      String status,
      UUID formDefinitionId,
      UUID workflowDefinitionId,
      String locale,
      String name,
      String description,
      String category
  ) {}

  public record CatalogItemDetail(
      UUID id,
      String key,
      String status,
      UUID formDefinitionId,
      UUID workflowDefinitionId,
      String eligibilityRulesJson,
      String locale,
      String name,
      String description,
      String category,
      Map<String, CatalogItem.Translation> translations,
      List<BundleComponent> components
  ) {
    public CatalogItemDetail(
        UUID id,
        String key,
        String status,
        UUID formDefinitionId,
        UUID workflowDefinitionId,
        String eligibilityRulesJson,
        String locale,
        String name,
        String description,
        String category
    ) {
      this(id, key, status, formDefinitionId, workflowDefinitionId, eligibilityRulesJson, locale, name, description, category, Map.of(), List.of());
    }
  }

  public record BundleComponent(UUID id, String key, int quantity, int position) {}
}
