package ru.ultimavox.itsm.platform.forms;

import java.util.List;
import java.util.Optional;

public interface FormDefinitionRepository {

    Optional<FormDefinition> findActiveByObjectKey(String objectKey);

    Optional<FormDefinition> findActiveByKey(String formKey);

    FormDefinition save(FormDefinition definition, String definitionJson);

    List<FormDefinitionVersion> findVersions(String formKey);

    FormDefinition insertNextDraft(FormDefinition definition, String definitionJson);

    FormDefinition publish(String formKey, int version);
}
