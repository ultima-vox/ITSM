package ru.ultimavox.itsm.platform.users;

import java.util.Map;

public record UserProfile(
    String subjectId,
    String username,
    String displayName,
    String email,
    String avatarUrl
) {
  /** Derive display name from raw subject ID when no profile exists. */
  public static UserProfile fallback(String subjectId) {
    String name = subjectId.contains("@") ? subjectId.substring(0, subjectId.indexOf('@')) : subjectId;
    String initials = name.length() >= 2 ? name.substring(0, 2).toUpperCase() : name.toUpperCase();
    return new UserProfile(subjectId, name, name, null, null);
  }

  public Map<String, Object> toMap() {
    return Map.of(
        "subjectId", subjectId,
        "username", username != null ? username : subjectId,
        "displayName", displayName != null ? displayName : subjectId,
        "email", email != null ? email : "",
        "avatarUrl", avatarUrl != null ? avatarUrl : ""
    );
  }
}
