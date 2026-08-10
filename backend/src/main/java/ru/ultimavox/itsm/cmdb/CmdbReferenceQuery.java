package ru.ultimavox.itsm.cmdb;

import java.util.UUID;

/** Stable public CMDB contract for validating references from other modules. */
public interface CmdbReferenceQuery {
  boolean exists(UUID configurationItemId);
}
