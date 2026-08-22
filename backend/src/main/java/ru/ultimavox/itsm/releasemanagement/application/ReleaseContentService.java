package ru.ultimavox.itsm.releasemanagement.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.changemanagement.ChangeCatalogQuery;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.observability.CorrelationContext;
import ru.ultimavox.itsm.releasemanagement.domain.Release;

/** The change content of a release: what ships, and whether it is allowed to ship. */
@Service
public class ReleaseContentService {
  /** A change is deployable once the CAB has approved it; earlier states still block the release. */
  private static final Set<String> DEPLOYABLE = Set.of(
      "APPROVED", "SCHEDULED", "IMPLEMENTING", "REVIEW", "CLOSED");

  private final JdbcTemplate jdbc;
  private final ReleaseQuery query;
  private final ChangeCatalogQuery changes;
  private final AuditTrail audit;

  public ReleaseContentService(
      JdbcTemplate jdbc,
      ReleaseQuery query,
      ChangeCatalogQuery changes,
      AuditTrail audit
  ) {
    this.jdbc = jdbc;
    this.query = query;
    this.changes = changes;
    this.audit = audit;
  }

  public List<ContentEntry> content(UUID releaseId) {
    requireRelease(releaseId);
    List<UUID> ids = query.changeIds(releaseId);
    if (ids.isEmpty()) {
      return List.of();
    }
    Map<UUID, ChangeCatalogQuery.ChangeSummary> byId = changes.summaries(ids).stream()
        .collect(Collectors.toMap(ChangeCatalogQuery.ChangeSummary::id, Function.identity()));
    return ids.stream()
        .map(byId::get)
        .filter(java.util.Objects::nonNull)
        .map(summary -> new ContentEntry(
            summary.id(),
            summary.number(),
            summary.title(),
            summary.type(),
            summary.status(),
            summary.plannedStart(),
            summary.plannedEnd(),
            DEPLOYABLE.contains(summary.status())
        ))
        .toList();
  }

  /** Linked changes that are not yet approved, and therefore block the deployment gate. */
  public List<ContentEntry> notReadyForDeployment(UUID releaseId) {
    return content(releaseId).stream().filter(entry -> !entry.deployable()).toList();
  }

  @Transactional
  public List<ContentEntry> link(UUID releaseId, List<UUID> changeIds, String actor) {
    Release release = requireRelease(releaseId);
    if (release.contentFrozen()) {
      throw new IllegalStateException("A release that reached deployment cannot change its content");
    }
    List<UUID> known = changes.summaries(changeIds).stream()
        .map(ChangeCatalogQuery.ChangeSummary::id)
        .toList();
    List<UUID> unknown = changeIds.stream().filter(id -> !known.contains(id)).toList();
    if (!unknown.isEmpty()) {
      throw new IllegalArgumentException("Unknown changes: " + unknown);
    }
    String org = OrganizationContext.current();
    Instant now = Instant.now();
    for (UUID changeId : known) {
      jdbc.update(
          """
              INSERT INTO release_change (release_id, change_id, org_id, added_by, added_at)
              VALUES (?,?,?,?,?)
              ON CONFLICT (release_id, change_id) DO NOTHING
              """,
          releaseId, changeId, org, actor, java.sql.Timestamp.from(now));
    }
    audit.append(new AuditTrail.Entry(actor, "release.content-linked", "release", releaseId.toString(),
        Map.of(), Map.of("changeIds", known.stream().map(UUID::toString).toList()),
        CorrelationContext.currentOrCreate(), now));
    return content(releaseId);
  }

  @Transactional
  public List<ContentEntry> unlink(UUID releaseId, UUID changeId, String actor) {
    Release release = requireRelease(releaseId);
    if (release.contentFrozen()) {
      throw new IllegalStateException("A release that reached deployment cannot change its content");
    }
    int removed = jdbc.update(
        "DELETE FROM release_change WHERE release_id = ? AND change_id = ? AND org_id = ?",
        releaseId, changeId, OrganizationContext.current());
    if (removed == 0) {
      throw new IllegalArgumentException("Change is not part of this release: " + changeId);
    }
    audit.append(new AuditTrail.Entry(actor, "release.content-unlinked", "release", releaseId.toString(),
        Map.of("changeId", changeId.toString()), Map.of(),
        CorrelationContext.currentOrCreate(), Instant.now()));
    return content(releaseId);
  }

  private Release requireRelease(UUID releaseId) {
    return query.findById(releaseId)
        .orElseThrow(() -> new IllegalArgumentException("Release not found: " + releaseId));
  }

  public record ContentEntry(
      UUID changeId,
      String number,
      String title,
      String type,
      String status,
      Instant plannedStart,
      Instant plannedEnd,
      boolean deployable
  ) {}
}
