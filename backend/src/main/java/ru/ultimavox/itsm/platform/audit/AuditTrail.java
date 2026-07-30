package ru.ultimavox.itsm.platform.audit;
import java.time.Instant; import java.util.Map; import java.util.UUID;
/** Platform port. Domain modules request audit recording; they do not write platform tables themselves. */
public interface AuditTrail {
  void append(Entry entry);
  record Entry(String actorId, String action, String objectType, String objectId, Map<String,Object> before, Map<String,Object> after, UUID correlationId, Instant occurredAt) {}
}
