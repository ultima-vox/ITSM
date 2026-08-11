package ru.ultimavox.itsm.platform.authorization;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.observability.CorrelationContext;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

@Service
public class RoleDelegationService {
  private static final Duration MAX_DURATION = Duration.ofDays(90);
  private final JdbcTemplate jdbc;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;

  public RoleDelegationService(JdbcTemplate jdbc, AuditTrail audit, IntegrationEventOutbox outbox) {
    this.jdbc = jdbc;
    this.audit = audit;
    this.outbox = outbox;
  }

  public List<Delegation> list() {
    return jdbc.query("""
        SELECT d.id,d.delegator_id,d.delegatee_id,r.role_key,d.starts_at,d.expires_at,
               d.reason,d.created_by,d.created_at,d.revoked_by,d.revoked_at
        FROM role_delegation d JOIN role r ON r.id=d.role_id
        WHERE d.org_id=? ORDER BY d.created_at DESC
        """, (rs, row) -> new Delegation(
        rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
        rs.getTimestamp(5).toInstant(), rs.getTimestamp(6).toInstant(), rs.getString(7),
        rs.getString(8), rs.getTimestamp(9).toInstant(), rs.getString(10),
        rs.getTimestamp(11) == null ? null : rs.getTimestamp(11).toInstant()),
        OrganizationContext.current());
  }

  @Transactional
  public Delegation create(Command command, String actor) {
    Instant now = Instant.now();
    String delegator = text(command.delegatorId(), "delegatorId");
    String delegatee = text(command.delegateeId(), "delegateeId");
    String roleKey = text(command.roleKey(), "roleKey");
    String reason = text(command.reason(), "reason");
    Instant starts = command.startsAt() == null ? now : command.startsAt();
    Instant expires = command.expiresAt();
    if (delegator.equals(delegatee)) throw new IllegalArgumentException("Self-delegation is forbidden");
    if (expires == null || !expires.isAfter(starts) || Duration.between(starts, expires).compareTo(MAX_DURATION) > 0) {
      throw new IllegalArgumentException("Delegation window must be positive and at most 90 days");
    }
    if (starts.isBefore(now.minus(Duration.ofMinutes(5)))) {
      throw new IllegalArgumentException("Delegation cannot start in the past");
    }
    List<UUID> roles = jdbc.query("""
        SELECT r.id FROM principal_role pr JOIN role r ON r.id=pr.role_id
        WHERE pr.org_id=? AND pr.subject_id=? AND r.role_key=?
          AND r.role_key <> 'ADMIN'
          AND NOT EXISTS (
            SELECT 1 FROM role_permission rp JOIN permission p ON p.id=rp.permission_id
            WHERE rp.role_id=r.id AND p.permission_key IN ('admin.full','rbac.write','rbac.delegate'))
        """, (rs, row) -> rs.getObject(1, UUID.class),
        OrganizationContext.current(), delegator, roleKey);
    if (roles.isEmpty()) {
      throw new IllegalArgumentException("Delegator does not directly hold a delegable role");
    }
    UUID id = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO role_delegation
          (id,org_id,delegator_id,delegatee_id,role_id,starts_at,expires_at,reason,created_by)
        VALUES (?,?,?,?,?,?,?,?,?)
        """, id, OrganizationContext.current(), delegator, delegatee, roles.getFirst(),
        Timestamp.from(starts), Timestamp.from(expires), reason, actor);
    Map<String,Object> state = Map.of("delegatorId", delegator, "delegateeId", delegatee,
        "roleKey", roleKey, "startsAt", starts.toString(), "expiresAt", expires.toString());
    recordChange(actor, "rbac.delegation-created", id, Map.of(), state, now);
    return new Delegation(id, delegator, delegatee, roleKey, starts, expires, reason, actor, now, null, null);
  }

  @Transactional
  public void revoke(UUID id, String actor) {
    Instant now = Instant.now();
    int changed = jdbc.update("""
        UPDATE role_delegation SET revoked_by=?,revoked_at=?
        WHERE id=? AND org_id=? AND revoked_at IS NULL
        """, actor, Timestamp.from(now), id, OrganizationContext.current());
    if (changed == 0) throw new IllegalArgumentException("Active delegation not found");
    recordChange(actor, "rbac.delegation-revoked", id, Map.of(), Map.of("revoked", true), now);
  }

  private void recordChange(String actor, String action, UUID id,
                            Map<String,Object> before, Map<String,Object> after, Instant now) {
    UUID correlation = CorrelationContext.currentOrCreate();
    audit.append(new AuditTrail.Entry(actor, action, "role-delegation", id.toString(),
        before, after, correlation, now));
    outbox.record(new DomainEvent(UUID.randomUUID(), action, 1, now, correlation,
        "role-delegation", id.toString(), after));
  }

  private static String text(String value, String field) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    return value.trim();
  }

  public record Command(String delegatorId, String delegateeId, String roleKey,
                        Instant startsAt, Instant expiresAt, String reason) {}
  public record Delegation(UUID id, String delegatorId, String delegateeId, String roleKey,
                           Instant startsAt, Instant expiresAt, String reason, String createdBy,
                           Instant createdAt, String revokedBy, Instant revokedAt) {}
}
