package ru.ultimavox.itsm.platform.announcement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.ultimavox.itsm.platform.announcement.AnnouncementService;
import ru.ultimavox.itsm.platform.authorization.AccessControl;

@RestController
@RequestMapping("/api/v1/announcements")
@Tag(name = "Announcements")
class AnnouncementController {
  private final AnnouncementService announcements;
  private final AccessControl access;

  AnnouncementController(AnnouncementService announcements, AccessControl access) {
    this.announcements = announcements;
    this.access = access;
  }

  @GetMapping("/active")
  @Operation(summary = "Announcements addressed to the caller right now")
  List<AnnouncementService.Announcement> active(Authentication authentication) {
    String actor = authentication.getName();
    access.require(actor, "announcement.read", "announcement", null);
    return announcements.active(audienceOf(actor), Instant.now());
  }

  @GetMapping
  @Operation(summary = "List every announcement, published or not")
  List<AnnouncementService.Announcement> list(Authentication authentication) {
    access.require(authentication.getName(), "announcement.admin", "announcement", null);
    return announcements.list();
  }

  @PostMapping
  @Operation(summary = "Create an announcement")
  ResponseEntity<AnnouncementService.Announcement> create(
      Authentication authentication, @Valid @RequestBody AnnouncementRequest body) {
    access.require(authentication.getName(), "announcement.admin", "announcement", null);
    AnnouncementService.Announcement created = execute(
        () -> announcements.create(body.toCommand(), authentication.getName()));
    return ResponseEntity.created(URI.create("/api/v1/announcements/" + created.id())).body(created);
  }

  @PatchMapping("/{id}")
  @Operation(summary = "Update an announcement with optimistic locking")
  AnnouncementService.Announcement update(Authentication authentication, @PathVariable UUID id,
                                          @Valid @RequestBody AnnouncementRequest body) {
    access.require(authentication.getName(), "announcement.admin", "announcement", id.toString());
    return execute(() -> announcements.update(
        id, body.expectedVersion() == null ? 0L : body.expectedVersion(),
        body.toCommand(), authentication.getName()));
  }

  @PostMapping("/{id}/retire")
  @Operation(summary = "End an announcement now, keeping it in the record")
  AnnouncementService.Announcement retire(Authentication authentication, @PathVariable UUID id) {
    access.require(authentication.getName(), "announcement.admin", "announcement", id.toString());
    return execute(() -> announcements.retire(id, authentication.getName()));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete an announcement")
  ResponseEntity<Void> delete(Authentication authentication, @PathVariable UUID id) {
    access.require(authentication.getName(), "announcement.admin", "announcement", id.toString());
    execute(() -> {
      announcements.delete(id, authentication.getName());
      return null;
    });
    return ResponseEntity.noContent().build();
  }

  /** An operator who may read work items is an agent; anyone else is a requester. */
  private AnnouncementService.Audience audienceOf(String actor) {
    return access.isAllowed(actor, "work-item.read", "work-item", null)
        ? AnnouncementService.Audience.AGENTS
        : AnnouncementService.Audience.REQUESTERS;
  }

  private static <T> T execute(java.util.function.Supplier<T> action) {
    try {
      return action.get();
    } catch (OptimisticLockingFailureException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
    } catch (IllegalArgumentException ex) {
      HttpStatus status = ex.getMessage() != null && ex.getMessage().startsWith("Announcement not found")
          ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
      throw new ResponseStatusException(status, ex.getMessage());
    }
  }

  record AnnouncementRequest(
      @NotBlank @Size(max = 240) String title,
      @NotBlank @Size(max = 8000) String body,
      @NotNull AnnouncementService.Severity severity,
      @NotNull AnnouncementService.Audience audience,
      @NotNull Instant startsAt,
      Instant endsAt,
      Boolean published,
      Boolean dismissible,
      @Size(max = 500) String linkUrl,
      Long expectedVersion
  ) {
    AnnouncementService.Command toCommand() {
      return new AnnouncementService.Command(
          title, body, severity, audience, startsAt, endsAt,
          Boolean.TRUE.equals(published),
          dismissible == null || dismissible,
          linkUrl);
    }
  }
}
