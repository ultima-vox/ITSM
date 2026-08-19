package ru.ultimavox.itsm.platform.users.api;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.users.UserProfile;
import ru.ultimavox.itsm.platform.users.UserProfileService;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Directory")
class UserProfileController {
  private final UserProfileService service;
  private final AccessControl access;

  UserProfileController(UserProfileService service, AccessControl access) {
    this.service = service;
    this.access = access;
  }

  @GetMapping
  @Operation(summary = "Resolve subject IDs to user profiles (email stripped for non-admin)")
  Map<String, SafeProfile> resolve(
      Authentication authentication,
      @RequestParam("ids") @Size(max = 100) Collection<String> subjectIds
  ) {
    String actor = authentication.getName();
    access.require(actor, "work-item.read", "user-profile", null);
    boolean showEmail = access.isAllowed(actor, "admin.full", "user", null);
    Map<String, UserProfile> raw = service.resolve(subjectIds);
    return raw.entrySet().stream()
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            e -> SafeProfile.from(e.getValue(), showEmail)
        ));
  }

  @PostMapping
  @Operation(summary = "Create or update a user profile")
  UserProfile upsert(
      Authentication authentication,
      @RequestBody UpsertProfileRequest body
  ) {
    access.require(authentication.getName(), "admin.full", "user", body.subjectId());
    UserProfile profile = new UserProfile(
        body.subjectId(), body.username(), body.displayName(), body.email(), body.avatarUrl()
    );
    service.upsert(profile);
    return profile;
  }

  record SafeProfile(
      String subjectId,
      String username,
      String displayName,
      String avatarUrl
  ) {
    static SafeProfile from(UserProfile p, boolean includeEmail) {
      return new SafeProfile(
          p.subjectId(),
          p.username() != null ? p.username() : p.subjectId(),
          p.displayName() != null ? p.displayName() : p.subjectId(),
          p.avatarUrl()
      );
    }
  }

  record UpsertProfileRequest(
      String subjectId,
      String username,
      String displayName,
      String email,
      String avatarUrl
  ) {}
}
