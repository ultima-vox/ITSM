package ru.ultimavox.itsm.platform.users;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface UserProfileRepository {
  Map<String, UserProfile> findBySubjectIds(Collection<String> subjectIds, String orgId);

  void upsert(UserProfile profile, String orgId);
}
