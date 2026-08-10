package ru.ultimavox.itsm.servicedesk.application;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.platform.sla.SlaDeadlineCalculator;
import ru.ultimavox.itsm.platform.sla.WorkingCalendar;
import ru.ultimavox.itsm.platform.workflow.WorkflowEngine;
import ru.ultimavox.itsm.platform.workflow.WorkflowTransitionException;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Impact;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Priority;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.State;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Type;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Urgency;

@Service
public class CreateWorkItem {

  private static final WorkingCalendar DEFAULT_CALENDAR = new WorkingCalendar(
      ZoneId.of("Europe/Moscow"),
      Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
      LocalTime.of(9, 0),
      LocalTime.of(18, 0),
      Set.of()
  );

  private final JdbcTemplate jdbc;
  private final WorkItemStore store;
  private final AuditTrail audit;
  private final IntegrationEventOutbox outbox;
  private final ObjectProvider<SlaDeadlineCalculator> slaCalculator;
  private final ObjectProvider<WorkflowEngine> workflowEngine;
  private final WorkItemSearchIndexer searchIndexer;

  CreateWorkItem(
      JdbcTemplate jdbc,
      WorkItemStore store,
      AuditTrail audit,
      IntegrationEventOutbox outbox,
      ObjectProvider<SlaDeadlineCalculator> slaCalculator,
      ObjectProvider<WorkflowEngine> workflowEngine,
      WorkItemSearchIndexer searchIndexer
  ) {
    this.jdbc = jdbc;
    this.store = store;
    this.audit = audit;
    this.outbox = outbox;
    this.slaCalculator = slaCalculator;
    this.workflowEngine = workflowEngine;
    this.searchIndexer = searchIndexer;
  }

  @Transactional
  public Created create(Command command, String actorId) {
    validate(command);

    UUID id = UUID.randomUUID();
    UUID correlationId = ru.ultimavox.itsm.platform.observability.CorrelationContext.currentOrCreate();
    Instant now = Instant.now();

    Impact impact = command.impact() == null ? Impact.MEDIUM : command.impact();
    Urgency urgency = command.urgency() == null ? Urgency.MEDIUM : command.urgency();
    Priority priority = WorkItem.derivePriority(impact, urgency);

    String prefix = command.type() == Type.INCIDENT ? "INC" : "REQ";
    Long sequence = jdbc.queryForObject("SELECT nextval('work_item_number_seq')", Long.class);
    String number = "%s-%06d".formatted(prefix, sequence);

    WorkItem item = new WorkItem(
        id,
        number,
        command.type(),
        command.title().trim(),
        command.description().trim(),
        command.service().trim(),
        State.NEW,
        priority,
        impact,
        urgency,
        command.assigneeId(),
        actorId,
        command.teamId(),
        null,
        null,
        false,
        now,
        now,
        null
    );

    store.insert(item);
    startSlaIfAvailable(item, now);
    ensureWorkflowIfAvailable(item);

    Map<String, Object> after = snapshot(item);
    audit.append(new AuditTrail.Entry(
        actorId,
        "work-item.created",
        "work-item",
        id.toString(),
        Map.of(),
        after,
        correlationId,
        now
    ));

    String eventType = command.type() == Type.INCIDENT ? "incident.created" : "service-request.created";
    outbox.record(new DomainEvent(
        UUID.randomUUID(),
        eventType,
        1,
        now,
        correlationId,
        "work-item",
        id.toString(),
        after
    ));

    searchIndexer.index(item);

    return new Created(id, number, State.NEW.name(), priority.name());
  }

  private void startSlaIfAvailable(WorkItem item, Instant now) {
    SlaDeadlineCalculator calculator = slaCalculator.getIfAvailable();
    if (calculator == null) {
      return;
    }
    Duration target = responseTarget(item.priority());
    Instant dueAt = calculator.deadline(now, target, DEFAULT_CALENDAR);
    Instant warningAt = calculator.deadline(now, target.dividedBy(2), DEFAULT_CALENDAR);
    store.startResponseSla(item.id(), now, dueAt, warningAt);
  }

  /** Starts a platform workflow instance when WorkflowEngine and a definition are present. */
  private void ensureWorkflowIfAvailable(WorkItem item) {
    WorkflowEngine engine = workflowEngine.getIfAvailable();
    if (engine == null) {
      return;
    }
    try {
      engine.ensureStarted("work-item", item.id().toString());
    } catch (WorkflowTransitionException ex) {
      // No active definition — local lifecycle still applies.
    }
  }

  private static Duration responseTarget(Priority priority) {
    return switch (priority) {
      case CRITICAL -> Duration.ofHours(4);
      case HIGH -> Duration.ofHours(8);
      case MEDIUM -> Duration.ofHours(24);
      case LOW -> Duration.ofHours(72);
    };
  }

  private static void validate(Command command) {
    if (command.type() == null) {
      throw new IllegalArgumentException("type is required");
    }
    if (command.title() == null || command.title().isBlank()) {
      throw new IllegalArgumentException("title is required");
    }
    if (command.description() == null || command.description().isBlank()) {
      throw new IllegalArgumentException("description is required");
    }
    if (command.service() == null || command.service().isBlank()) {
      throw new IllegalArgumentException("service is required");
    }
  }

  static Map<String, Object> snapshot(WorkItem item) {
    Map<String, Object> map = new HashMap<>();
    map.put("number", item.number());
    map.put("type", item.type().name());
    map.put("title", item.title());
    map.put("description", item.description());
    map.put("service", item.service());
    map.put("state", item.state().name());
    map.put("priority", item.priority().name());
    map.put("impact", item.impact().name());
    map.put("urgency", item.urgency().name());
    map.put("assigneeId", item.assigneeId());
    map.put("requesterId", item.requesterId());
    map.put("teamId", item.teamId());
    map.put("resolutionCode", item.resolutionCode());
    map.put("resolutionNotes", item.resolutionNotes());
    map.put("escalated", item.escalated());
    map.put("closedAt", item.closedAt() == null ? null : item.closedAt().toString());
    map.put("updatedAt", item.updatedAt().toString());
    return map;
  }

  public record Command(
      Type type,
      String title,
      String description,
      String service,
      Impact impact,
      Urgency urgency,
      String assigneeId,
      String teamId
  ) {
    /** Backward-compatible constructor used by existing tests and simple callers. */
    public Command(Type type, String title, String description, String service) {
      this(type, title, description, service, Impact.MEDIUM, Urgency.MEDIUM, null, null);
    }
  }

  public record Created(UUID id, String number, String state, String priority) {
    /** Compatibility for callers that only assert id/number/state. */
    public Created(UUID id, String number, String state) {
      this(id, number, state, Priority.MEDIUM.name());
    }
  }
}
