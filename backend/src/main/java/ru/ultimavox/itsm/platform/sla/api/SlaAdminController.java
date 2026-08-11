package ru.ultimavox.itsm.platform.sla.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import java.time.Duration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.sla.SlaPolicy;
import ru.ultimavox.itsm.platform.sla.SlaPolicyRepository;
import ru.ultimavox.itsm.platform.sla.SlaPolicyRepository.SlaPolicyView;
import ru.ultimavox.itsm.platform.sla.SlaPolicyAdminService;
import ru.ultimavox.itsm.platform.sla.WorkingCalendar;
import ru.ultimavox.itsm.platform.sla.WorkingCalendarAdminService;
import ru.ultimavox.itsm.platform.sla.WorkingCalendarRegistry.WorkingCalendarView;

@RestController
@RequestMapping("/api/v1/sla")
@Tag(name = "Platform — SLA")
class SlaAdminController {

  private final SlaPolicyRepository policies;
  private final AccessControl access;
  private final SlaPolicyAdminService admin;
  private final WorkingCalendarAdminService calendars;

  SlaAdminController(SlaPolicyRepository policies, SlaPolicyAdminService admin, AccessControl access,
      WorkingCalendarAdminService calendars) {
    this.policies = policies;
    this.admin = admin;
    this.access = access;
    this.calendars = calendars;
  }

  @GetMapping("/calendars")
  @Operation(summary = "List working calendars for current organization")
  List<CalendarResponse> listCalendars(Authentication authentication) {
    access.require(authentication.getName(), "sla.read", "working_calendar", null);
    return calendars.list().stream().map(CalendarResponse::from).toList();
  }

  @org.springframework.web.bind.annotation.PostMapping("/calendars")
  @Operation(summary = "Create a working calendar")
  CalendarResponse createCalendar(Authentication authentication, @RequestBody CalendarRequest body) {
    access.require(authentication.getName(), "sla.write", "working_calendar", null);
    try {
      return CalendarResponse.from(calendars.create(authentication.getName(), body.toCommand()));
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    }
  }

  @PatchMapping("/calendars/{id}")
  @Operation(summary = "Update a working calendar with optimistic locking")
  CalendarResponse updateCalendar(Authentication authentication, @PathVariable UUID id,
      @RequestBody CalendarRequest body) {
    access.require(authentication.getName(), "sla.write", "working_calendar", id.toString());
    try {
      return CalendarResponse.from(calendars.update(
          authentication.getName(), id, body.expectedVersion(), body.toCommand()));
    } catch (org.springframework.dao.OptimisticLockingFailureException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  @GetMapping("/policies")
  @Operation(summary = "List SLA policies (admin read)")
  List<PolicyResponse> listPolicies(Authentication authentication) {
    String actor = authentication != null ? authentication.getName() : null;
    access.require(actor, "sla.read", "sla_policy", null);
    return policies.listAll().stream().map(PolicyResponse::from).toList();
  }

  @PatchMapping("/policies/{id}")
  @Operation(summary = "Update SLA policy targets or enabled state for current organization")
  PolicyResponse updatePolicy(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody UpdatePolicyRequest body
  ) {
    String actor = authentication != null ? authentication.getName() : null;
    access.require(actor, "sla.write", "sla_policy", id.toString());
    if (body.targets() != null && body.targets().stream().anyMatch(t ->
        t.metric() == null || t.metric().isBlank() || t.targetMinutes() <= 0
            || t.warningBeforeMinutes() < 0)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid SLA target");
    }
    List<SlaPolicy.Target> targets = body.targets() == null ? null : body.targets().stream()
        .map(t -> new SlaPolicy.Target(
            t.metric(), t.condition(), Duration.ofMinutes(t.targetMinutes()),
            Duration.ofMinutes(t.warningBeforeMinutes())))
        .toList();
    return PolicyResponse.from(admin.update(actor, id, body.expectedVersion(), body.enabled(), targets));
  }

  record UpdatePolicyRequest(int expectedVersion, Boolean enabled, List<TargetResponse> targets) {}

  record CalendarRequest(long expectedVersion, String key, String zone,
      java.util.Set<java.time.DayOfWeek> workingDays, java.time.LocalTime startsAt,
      java.time.LocalTime endsAt, java.util.Set<java.time.LocalDate> holidays) {
    WorkingCalendarAdminService.Command toCommand() {
      return new WorkingCalendarAdminService.Command(key, zone, workingDays, startsAt, endsAt, holidays);
    }
  }

  record CalendarResponse(UUID id, String key, String zone, List<String> workingDays,
      String startsAt, String endsAt, List<String> holidays, long version) {
    static CalendarResponse from(WorkingCalendarView view) {
      WorkingCalendar c = view.calendar();
      return new CalendarResponse(view.id(), view.key(), c.zone().getId(),
          c.workingDays().stream().sorted().map(Enum::name).toList(),
          c.startsAt().toString(), c.endsAt().toString(),
          c.holidays().stream().sorted().map(Object::toString).toList(), view.version());
    }
  }

  record PolicyResponse(
      UUID id,
      String key,
      String calendarKey,
      boolean enabled,
      int version,
      List<TargetResponse> targets,
      List<String> pauseStates
  ) {
    static PolicyResponse from(SlaPolicyView view) {
      SlaPolicy p = view.policy();
      return new PolicyResponse(
          p.id(),
          p.key(),
          p.calendarKey(),
          view.enabled(),
          view.version(),
          p.targets().stream()
              .map(t -> new TargetResponse(
                  t.metric(),
                  t.condition(),
                  t.target() == null ? 0L : t.target().toMinutes(),
                  t.warningBefore() == null ? 0L : t.warningBefore().toMinutes()
              ))
              .toList(),
          List.copyOf(p.pauseStates())
      );
    }
  }

  record TargetResponse(
      String metric,
      String condition,
      long targetMinutes,
      long warningBeforeMinutes
  ) {}
}
