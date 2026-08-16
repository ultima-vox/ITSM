package ru.ultimavox.itsm.changemanagement.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.changemanagement.domain.Change;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

@Service
public class CabVoteService {

  public static final int QUORUM_APPROVES = 2;

  private final JdbcTemplate jdbc;
  private final ChangeQuery query;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;

  public CabVoteService(
      JdbcTemplate jdbc,
      ChangeQuery query,
      AuditTrail audit,
      IntegrationEventOutbox outbox
  ) {
    this.jdbc = jdbc;
    this.query = query;
    this.audit = audit;
    this.outbox = outbox;
  }

  public List<CabVote> listVotes(UUID changeId) {
    query.findById(changeId)
        .orElseThrow(() -> new IllegalArgumentException("Change not found: " + changeId));
    return jdbc.query(
        """
        SELECT id, change_id, approver_id, decision, decided_at, comment
        FROM change_approval
        WHERE change_id = ?
        ORDER BY decided_at ASC NULLS LAST, id ASC
        """,
        (rs, i) -> new CabVote(
            rs.getObject("id", UUID.class),
            rs.getObject("change_id", UUID.class),
            rs.getString("approver_id"),
            rs.getString("decision"),
            rs.getTimestamp("decided_at") == null
                ? null
                : rs.getTimestamp("decided_at").toInstant(),
            rs.getString("comment")
        ),
        changeId
    );
  }

  public long countApproves(UUID changeId) {
    Long n = jdbc.queryForObject(
        """
        SELECT count(*) FROM change_approval
        WHERE change_id = ?
          AND decision IN ('APPROVED', 'APPROVE', 'approve')
        """,
        Long.class,
        changeId
    );
    return n == null ? 0L : n;
  }

  /**
   * Cast or update a CAB member vote. Decision: APPROVE | REJECT.
   * Does not auto-transition the change — chair still calls transition.
   */
  @Transactional
  public CabVote castVote(UUID changeId, String decision, String comment, String actor) {
    Change change = query.findById(changeId)
        .orElseThrow(() -> new IllegalArgumentException("Change not found: " + changeId));
    if (change.status() != Change.Status.CAB_REVIEW) {
      throw new IllegalStateException("Votes allowed only during CAB_REVIEW");
    }
    String normalized = normalizeDecision(decision);
    Instant now = Instant.now();
    UUID correlationId = UUID.randomUUID();

    // Replace prior vote from same approver
    jdbc.update(
        "DELETE FROM change_approval WHERE change_id = ? AND approver_id = ?",
        changeId,
        actor
    );
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO change_approval (id, change_id, approver_id, decision, decided_at, comment)
        VALUES (?,?,?,?,?,?)
        """,
        id,
        changeId,
        actor,
        normalized,
        java.sql.Timestamp.from(now),
        comment
    );

    Map<String, Object> after = Map.of(
        "decision", normalized,
        "approverId", actor,
        "approveCount", countApproves(changeId)
    );
    audit.append(new AuditTrail.Entry(
        actor, "change.cab-vote", "change", changeId.toString(),
        Map.of(), after, correlationId, now
    ));
    outbox.record(new DomainEvent(
        UUID.randomUUID(), "change.cab-vote", 1, now, correlationId,
        "change", changeId.toString(), after
    ));
    return new CabVote(id, changeId, actor, normalized, now, comment);
  }

  private static String normalizeDecision(String decision) {
    if (decision == null || decision.isBlank()) {
      throw new IllegalArgumentException("decision is required");
    }
    String d = decision.trim().toUpperCase();
    if (d.equals("APPROVE") || d.equals("APPROVED")) {
      return "APPROVED";
    }
    if (d.equals("REJECT") || d.equals("REJECTED")) {
      return "REJECTED";
    }
    throw new IllegalArgumentException("decision must be APPROVE or REJECT");
  }

  public record CabVote(
      UUID id,
      UUID changeId,
      String approverId,
      String decision,
      Instant decidedAt,
      String comment
  ) {}
}
