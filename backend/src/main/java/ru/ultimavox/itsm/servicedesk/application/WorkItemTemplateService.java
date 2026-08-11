package ru.ultimavox.itsm.servicedesk.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.observability.CorrelationContext;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;

@Service
public class WorkItemTemplateService {
  private final JdbcTemplate jdbc;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;

  WorkItemTemplateService(JdbcTemplate jdbc, AuditTrail audit, IntegrationEventOutbox outbox) {
    this.jdbc = jdbc;
    this.audit = audit;
    this.outbox = outbox;
  }

  public List<Template> list(boolean includeInactive) {
    String activeClause = includeInactive ? "" : " AND active";
    return jdbc.query(
        """
        SELECT id,name,type,title,description,service,impact,urgency,team_id,active,version,
               created_by,created_at,updated_at
        FROM work_item_template WHERE org_id=?
        """ + activeClause + " ORDER BY name",
        (rs, rowNum) -> new Template(
            rs.getObject("id", UUID.class), rs.getString("name"),
            WorkItem.Type.valueOf(rs.getString("type")), rs.getString("title"),
            rs.getString("description"), rs.getString("service"),
            WorkItem.Impact.valueOf(rs.getString("impact")),
            WorkItem.Urgency.valueOf(rs.getString("urgency")), rs.getString("team_id"),
            rs.getBoolean("active"), rs.getLong("version"), rs.getString("created_by"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()
        ), OrganizationContext.current()
    );
  }

  @Transactional
  public Template create(Command command, String actor) {
    validate(command);
    Instant now = Instant.now();
    Template template = new Template(
        UUID.randomUUID(), command.name().trim(), command.type(), command.title().trim(),
        command.description().trim(), command.service().trim(), command.impact(), command.urgency(),
        trimToNull(command.teamId()), true, 0, actor, now, now
    );
    jdbc.update(
        """
        INSERT INTO work_item_template(
          id,org_id,name,type,title,description,service,impact,urgency,team_id,active,version,
          created_by,created_at,updated_at
        ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        template.id(), OrganizationContext.current(), template.name(), template.type().name(),
        template.title(), template.description(), template.service(), template.impact().name(),
        template.urgency().name(), template.teamId(), true, 0, actor,
        Timestamp.from(now), Timestamp.from(now)
    );
    record("work-item-template.created", template.id(), actor, now);
    return template;
  }

  @Transactional
  public void update(UUID id, long version, Command command, String actor) {
    validate(command);
    Instant now = Instant.now();
    int updated = jdbc.update(
        """
        UPDATE work_item_template SET
          name=?,type=?,title=?,description=?,service=?,impact=?,urgency=?,team_id=?,
          version=version+1,updated_at=?
        WHERE id=? AND org_id=? AND version=?
        """,
        command.name().trim(), command.type().name(), command.title().trim(),
        command.description().trim(), command.service().trim(), command.impact().name(),
        command.urgency().name(), trimToNull(command.teamId()), Timestamp.from(now),
        id, OrganizationContext.current(), version
    );
    if (updated != 1) throw new IllegalStateException("Template not found or changed concurrently");
    record("work-item-template.updated", id, actor, now);
  }

  @Transactional
  public void archive(UUID id, long version, String actor) {
    Instant now = Instant.now();
    int updated = jdbc.update(
        """
        UPDATE work_item_template SET active=false,version=version+1,updated_at=?
        WHERE id=? AND org_id=? AND version=? AND active
        """,
        Timestamp.from(now), id, OrganizationContext.current(), version
    );
    if (updated != 1) throw new IllegalStateException("Template not found or changed concurrently");
    record("work-item-template.archived", id, actor, now);
  }

  private static void validate(Command command) {
    if (command == null || blank(command.name()) || command.type() == null || blank(command.title())
        || blank(command.description()) || blank(command.service()) || command.impact() == null
        || command.urgency() == null) {
      throw new IllegalArgumentException("Template fields are required");
    }
  }

  private void record(String action, UUID id, String actor, Instant now) {
    UUID correlation = CorrelationContext.currentOrCreate();
    Map<String, Object> data = Map.of("templateId", id.toString());
    audit.append(new AuditTrail.Entry(actor, action, "work-item-template", id.toString(),
        Map.of(), data, correlation, now));
    outbox.record(new DomainEvent(UUID.randomUUID(), action, 1, now, correlation,
        "work-item-template", id.toString(), data));
  }

  private static boolean blank(String value) { return value == null || value.isBlank(); }
  private static String trimToNull(String value) { return blank(value) ? null : value.trim(); }

  public record Command(String name, WorkItem.Type type, String title, String description,
                        String service, WorkItem.Impact impact, WorkItem.Urgency urgency,
                        String teamId) {}
  public record Template(UUID id, String name, WorkItem.Type type, String title,
                         String description, String service, WorkItem.Impact impact,
                         WorkItem.Urgency urgency, String teamId, boolean active, long version,
                         String createdBy, Instant createdAt, Instant updatedAt) {}
}
