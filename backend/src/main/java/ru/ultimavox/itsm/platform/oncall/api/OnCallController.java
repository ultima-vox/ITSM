package ru.ultimavox.itsm.platform.oncall.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.oncall.OnCallAdminService;
import ru.ultimavox.itsm.platform.oncall.OnCallDirectory;

@RestController
@RequestMapping("/api/v1/oncall")
@Tag(name = "On-call")
class OnCallController {
  private final OnCallAdminService admin;
  private final OnCallDirectory directory;
  private final AccessControl access;

  OnCallController(OnCallAdminService admin, OnCallDirectory directory, AccessControl access) {
    this.admin = admin;
    this.directory = directory;
    this.access = access;
  }

  @GetMapping("/schedules")
  @Operation(summary = "List on-call schedules")
  List<OnCallAdminService.Schedule> listSchedules(Authentication authentication) {
    access.require(authentication.getName(), "oncall.read", "oncall", null);
    return admin.listSchedules();
  }

  @GetMapping("/schedules/{scheduleKey}")
  @Operation(summary = "Get an on-call schedule")
  OnCallAdminService.Schedule getSchedule(Authentication authentication,
                                          @PathVariable String scheduleKey) {
    access.require(authentication.getName(), "oncall.read", "oncall", scheduleKey);
    return execute(() -> admin.getSchedule(scheduleKey));
  }

  @GetMapping("/schedules/{scheduleKey}/current")
  @Operation(summary = "Who is on call for a schedule, now or at a given instant")
  OnCallNowResponse current(Authentication authentication,
                            @PathVariable String scheduleKey,
                            @RequestParam(required = false) Instant at) {
    access.require(authentication.getName(), "oncall.read", "oncall", scheduleKey);
    Instant when = at == null ? Instant.now() : at;
    return new OnCallNowResponse(scheduleKey, when, directory.onCall(scheduleKey, when).orElse(null));
  }

  @PostMapping("/schedules")
  @Operation(summary = "Create an on-call schedule")
  ResponseEntity<OnCallAdminService.Schedule> createSchedule(
      Authentication authentication, @Valid @RequestBody ScheduleRequest body) {
    access.require(authentication.getName(), "oncall.admin", "oncall", null);
    OnCallAdminService.Schedule created = execute(
        () -> admin.createSchedule(body.toCommand(), authentication.getName()));
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PutMapping("/schedules/{scheduleKey}")
  @Operation(summary = "Replace an on-call schedule and its rotation")
  OnCallAdminService.Schedule updateSchedule(Authentication authentication,
                                             @PathVariable String scheduleKey,
                                             @Valid @RequestBody ScheduleRequest body) {
    access.require(authentication.getName(), "oncall.admin", "oncall", scheduleKey);
    return execute(() -> admin.updateSchedule(scheduleKey, body.toCommand(), authentication.getName()));
  }

  @DeleteMapping("/schedules/{scheduleKey}")
  @Operation(summary = "Delete an on-call schedule")
  ResponseEntity<Void> deleteSchedule(Authentication authentication,
                                      @PathVariable String scheduleKey) {
    access.require(authentication.getName(), "oncall.admin", "oncall", scheduleKey);
    execute(() -> {
      admin.deleteSchedule(scheduleKey, authentication.getName());
      return null;
    });
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/schedules/{scheduleKey}/overrides")
  @Operation(summary = "List the overrides of a schedule")
  List<OnCallAdminService.Override> listOverrides(Authentication authentication,
                                                  @PathVariable String scheduleKey) {
    access.require(authentication.getName(), "oncall.read", "oncall", scheduleKey);
    return execute(() -> admin.listOverrides(scheduleKey));
  }

  @PostMapping("/schedules/{scheduleKey}/overrides")
  @Operation(summary = "Override who is on call for a window")
  ResponseEntity<OnCallAdminService.Override> addOverride(Authentication authentication,
                                                          @PathVariable String scheduleKey,
                                                          @Valid @RequestBody OverrideRequest body) {
    access.require(authentication.getName(), "oncall.admin", "oncall", scheduleKey);
    OnCallAdminService.Override created = execute(() -> admin.addOverride(
        scheduleKey,
        new OnCallAdminService.OverrideCommand(body.subject(), body.startsAt(), body.endsAt(), body.reason()),
        authentication.getName()));
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @DeleteMapping("/schedules/{scheduleKey}/overrides/{overrideId}")
  @Operation(summary = "Remove an override")
  ResponseEntity<Void> deleteOverride(Authentication authentication,
                                      @PathVariable String scheduleKey,
                                      @PathVariable UUID overrideId) {
    access.require(authentication.getName(), "oncall.admin", "oncall", scheduleKey);
    execute(() -> {
      admin.deleteOverride(scheduleKey, overrideId, authentication.getName());
      return null;
    });
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/policies")
  @Operation(summary = "List escalation policies")
  List<OnCallAdminService.Policy> listPolicies(Authentication authentication) {
    access.require(authentication.getName(), "oncall.read", "oncall", null);
    return admin.listPolicies();
  }

  @GetMapping("/policies/{policyKey}/chain")
  @Operation(summary = "Resolve the escalation chain of a policy to subjects")
  List<OnCallDirectory.Responder> chain(Authentication authentication,
                                        @PathVariable String policyKey,
                                        @RequestParam(required = false) Instant at) {
    access.require(authentication.getName(), "oncall.read", "oncall", policyKey);
    return directory.escalationChain(policyKey, at == null ? Instant.now() : at);
  }

  @PostMapping("/policies")
  @Operation(summary = "Create an escalation policy")
  ResponseEntity<OnCallAdminService.Policy> createPolicy(Authentication authentication,
                                                         @Valid @RequestBody PolicyRequest body) {
    access.require(authentication.getName(), "oncall.admin", "oncall", null);
    OnCallAdminService.Policy created = execute(
        () -> admin.createPolicy(body.toCommand(), authentication.getName()));
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PutMapping("/policies/{policyKey}")
  @Operation(summary = "Replace an escalation policy and its steps")
  OnCallAdminService.Policy updatePolicy(Authentication authentication,
                                         @PathVariable String policyKey,
                                         @Valid @RequestBody PolicyRequest body) {
    access.require(authentication.getName(), "oncall.admin", "oncall", policyKey);
    return execute(() -> admin.updatePolicy(policyKey, body.toCommand(), authentication.getName()));
  }

  @DeleteMapping("/policies/{policyKey}")
  @Operation(summary = "Delete an escalation policy")
  ResponseEntity<Void> deletePolicy(Authentication authentication, @PathVariable String policyKey) {
    access.require(authentication.getName(), "oncall.admin", "oncall", policyKey);
    execute(() -> {
      admin.deletePolicy(policyKey, authentication.getName());
      return null;
    });
    return ResponseEntity.noContent().build();
  }

  private static <T> T execute(java.util.function.Supplier<T> action) {
    try {
      return action.get();
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    } catch (IllegalArgumentException ex) {
      HttpStatus status = ex.getMessage() != null && ex.getMessage().contains("not found")
          ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
      throw new ResponseStatusException(status, ex.getMessage());
    }
  }

  record ScheduleRequest(
      @NotBlank @Size(max = 64) String scheduleKey,
      @NotBlank @Size(max = 160) String name,
      @Size(max = 64) String timeZone,
      @Min(1) @Max(8760) int rotationHours,
      @NotNull Instant rotationStart,
      Boolean active,
      @NotNull @Size(min = 1, max = 50) List<@NotBlank @Size(max = 128) String> participants
  ) {
    OnCallAdminService.ScheduleCommand toCommand() {
      return new OnCallAdminService.ScheduleCommand(
          scheduleKey, name,
          timeZone == null || timeZone.isBlank() ? "UTC" : timeZone,
          rotationHours, rotationStart, active == null || active, participants);
    }
  }

  record OverrideRequest(
      @NotBlank @Size(max = 128) String subject,
      @NotNull Instant startsAt,
      @NotNull Instant endsAt,
      @Size(max = 500) String reason
  ) {}

  record PolicyRequest(
      @NotBlank @Size(max = 64) String policyKey,
      @NotBlank @Size(max = 160) String name,
      Boolean active,
      @NotNull @Size(min = 1, max = 20) List<@NotNull StepRequest> steps
  ) {
    OnCallAdminService.PolicyCommand toCommand() {
      return new OnCallAdminService.PolicyCommand(
          policyKey, name, active == null || active,
          steps.stream().map(StepRequest::toCommand).toList());
    }
  }

  record StepRequest(
      @Min(0) @Max(10080) int delayMinutes,
      @NotBlank @Size(max = 16) String targetType,
      @NotBlank @Size(max = 128) String targetRef
  ) {
    OnCallAdminService.StepCommand toCommand() {
      return new OnCallAdminService.StepCommand(delayMinutes, targetType, targetRef);
    }
  }

  record OnCallNowResponse(String scheduleKey, Instant at, String subject) {}
}
