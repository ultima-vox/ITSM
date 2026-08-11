package ru.ultimavox.itsm.platform.metadata;

/** Object schema version plus publication state. Definitions themselves remain immutable. */
public record ObjectDefinitionVersion(ObjectDefinition definition, boolean active) {}
