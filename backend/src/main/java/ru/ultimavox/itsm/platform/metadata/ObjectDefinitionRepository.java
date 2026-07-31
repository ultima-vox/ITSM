package ru.ultimavox.itsm.platform.metadata;

import java.util.List;
import java.util.Optional;

/** Persistence port for versioned object definitions. */
public interface ObjectDefinitionRepository {

    Optional<ObjectDefinition> findActiveByKey(String objectKey);

    List<ObjectDefinition> findAllActive();

    Optional<ObjectDefinition> findByKeyAndVersion(String objectKey, int version);
}
