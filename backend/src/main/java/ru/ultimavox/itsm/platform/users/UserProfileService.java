package ru.ultimavox.itsm.platform.users;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

@Service
public class UserProfileService {
  private final UserProfileRepository repository;

  UserProfileService(UserProfileRepository repository) {
    this.repository = repository;
  }

  public Map<String, UserProfile> resolve(Collection<String> subjectIds) {
    String orgId = OrganizationContext.current();
    Map<String, UserProfile> found = repository.findBySubjectIds(subjectIds, orgId);
    // Fill fallbacks for unknown subject IDs
    Map<String, UserProfile> result = new java.util.LinkedHashMap<>(found);
    for (String sid : subjectIds) {
      if (!result.containsKey(sid)) {
        result.put(sid, UserProfile.fallback(sid));
      }
    }
    return result;
  }

  public void upsert(UserProfile profile) {
    repository.upsert(profile, OrganizationContext.current());
  }
}
