package ru.ultimavox.itsm.platform.forms;

import java.util.Optional;

public interface FormDefinitionRepository {

    Optional<FormDefinition> findActiveByObjectKey(String objectKey);

    Optional<FormDefinition> findActiveByKey(String formKey);

    FormDefinition save(FormDefinition definition, String definitionJson);
}
