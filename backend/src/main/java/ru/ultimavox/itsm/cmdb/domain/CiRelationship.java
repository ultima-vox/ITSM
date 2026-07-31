package ru.ultimavox.itsm.cmdb.domain;

import java.util.UUID;

/**
 * Direction is meaningful: source DEPENDS_ON target supports impact traversal
 * and avoids ambiguous graph edges.
 */
public record CiRelationship(UUID id, UUID sourceCiId, UUID targetCiId, Type type) {
  public enum Type { DEPENDS_ON, HOSTED_ON, CONNECTED_TO, RUNS_ON, LOCATED_IN, USES }
}
