package ru.ultimavox.itsm.servicecatalog.application;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.observability.CorrelationContext;

@Service
public class CatalogBundleAdminService {
  private final JdbcTemplate jdbc;
  private final AuditTrail audit;
  CatalogBundleAdminService(JdbcTemplate jdbc, AuditTrail audit) { this.jdbc=jdbc; this.audit=audit; }

  @Transactional
  public List<Component> replace(UUID bundleId, List<Component> components, String actor) {
    String org = OrganizationContext.current();
    requireItem(bundleId, org);
    List<Component> safe = components == null ? List.of() : List.copyOf(components);
    if (safe.size() != new HashSet<>(safe.stream().map(Component::itemId).toList()).size())
      throw new IllegalArgumentException("Duplicate bundle component");
    for (Component component : safe) {
      if (component.itemId().equals(bundleId)) throw new IllegalArgumentException("Bundle cannot contain itself");
      if (component.quantity() < 1 || component.quantity() > 100) throw new IllegalArgumentException("Quantity must be 1..100");
      requireItem(component.itemId(), org);
      Boolean cyclic = jdbc.queryForObject("""
          WITH RECURSIVE descendants(id) AS (
            SELECT component_item_id FROM catalog_bundle_component WHERE bundle_item_id=? AND org_id=?
            UNION SELECT bc.component_item_id FROM catalog_bundle_component bc JOIN descendants d ON bc.bundle_item_id=d.id WHERE bc.org_id=?
          ) SELECT EXISTS(SELECT 1 FROM descendants WHERE id=?)
          """, Boolean.class, component.itemId(), org, org, bundleId);
      if (Boolean.TRUE.equals(cyclic)) throw new IllegalArgumentException("Bundle cycle detected");
    }
    jdbc.update("DELETE FROM catalog_bundle_component WHERE bundle_item_id=? AND org_id=?", bundleId, org);
    for (Component component : safe) jdbc.update("INSERT INTO catalog_bundle_component(bundle_item_id,component_item_id,org_id,quantity,position) VALUES (?,?,?,?,?)",
        bundleId, component.itemId(), org, component.quantity(), component.position());
    Instant now=Instant.now();
    audit.append(new AuditTrail.Entry(actor,"catalog-item.bundle-replaced","catalog-item",bundleId.toString(),Map.of(),
        Map.of("components",safe.size()),CorrelationContext.currentOrCreate(),now));
    return safe;
  }
  private void requireItem(UUID id,String org) {
    Integer count=jdbc.queryForObject("SELECT count(*) FROM catalog_item WHERE id=? AND org_id=?",Integer.class,id,org);
    if(count==null||count!=1) throw new IllegalArgumentException("Catalog item not found");
  }
  public record Component(UUID itemId,int quantity,int position) {}
}
