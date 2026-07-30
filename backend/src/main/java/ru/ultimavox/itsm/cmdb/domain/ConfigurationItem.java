package ru.ultimavox.itsm.cmdb.domain;
import java.util.*;
/** Typed CI with relationship semantics used by impact analysis. */
public record ConfigurationItem(UUID id, String name, String classKey, Status status, Map<String,Object> attributes) { public enum Status { OPERATIONAL, DEGRADED, MAINTENANCE, RETIRED } }
