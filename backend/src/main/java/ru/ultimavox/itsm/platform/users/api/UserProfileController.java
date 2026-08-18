package ru.ultimavox.itsm.platform.users.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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
  @Operation(summary = "Resolve subject IDs to user profiles")
  Map<String, UserProfile> resolve(
      @RequestParam("ids") Collection<String> subjectIds
  ) {
    // No specific permission needed — any authenticated user can resolve display names
    return service.resolve(subjectIds);
  }

  @PostMapping
  @Operation(summary = "Create or update a user profile")
  UserProfile upsert(
      @AuthenticationPrincipal UserDetails user,
      @RequestBody UpsertProfileRequest body
  ) {
    access.require(user.getUsername(), "admin.full", "user", body.subjectId());
    UserProfile profile = new UserProfile(
        body.subjectId(), body.username(), body.displayName(), body.email(), body.avatarUrl()
    );
    service.upsert(profile);
    return profile;
  }

  record UpsertProfileRequest(
      String subjectId,
      String username,
      String displayName,
      String email,
      String avatarUrl
  ) {}
}
