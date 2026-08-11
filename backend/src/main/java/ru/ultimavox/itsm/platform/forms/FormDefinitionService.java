package ru.ultimavox.itsm.platform.forms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.forms.FormDefinition.Expression;
import ru.ultimavox.itsm.platform.forms.FormDefinition.Field;
import ru.ultimavox.itsm.platform.forms.FormDefinition.Section;
import ru.ultimavox.itsm.platform.forms.FormRenderModel.ExpressionModel;
import ru.ultimavox.itsm.platform.forms.FormRenderModel.FieldModel;
import ru.ultimavox.itsm.platform.forms.FormRenderModel.SectionModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Load/save form definitions and produce a renderable model for clients. */
@Service
public class FormDefinitionService {

    private final FormDefinitionRepository repository;
    private final ObjectMapper json;

    public FormDefinitionService(FormDefinitionRepository repository, ObjectMapper json) {
        this.repository = repository;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public Optional<FormDefinition> getActiveByObjectKey(String objectKey) {
        return repository.findActiveByObjectKey(objectKey);
    }

    @Transactional(readOnly = true)
    public Optional<FormDefinition> getActiveByKey(String formKey) {
        return repository.findActiveByKey(formKey);
    }

    @Transactional
    public FormDefinition save(FormDefinition definition) {
        return repository.save(definition, serialize(definition));
    }

    /**
     * Builds a client-ready render model with localization keys for each section and field.
     */
    @Transactional(readOnly = true)
    public Optional<FormRenderModel> renderForObject(String objectKey) {
        return repository.findActiveByObjectKey(objectKey).map(this::toRenderModel);
    }

    public FormRenderModel toRenderModel(FormDefinition definition) {
        List<SectionModel> sections = new ArrayList<>();
        for (Section section : definition.sections()) {
            List<FieldModel> fields = new ArrayList<>();
            for (Field field : section.fields()) {
                fields.add(new FieldModel(
                        field.attributeKey(),
                        field.required(),
                        "form." + definition.key() + ".field." + field.attributeKey(),
                        toExpressionModel(field.visibleWhen()),
                        toExpressionModel(field.readOnlyWhen())
                ));
            }
            sections.add(new SectionModel(
                    section.key(),
                    section.labels(),
                    "form." + definition.key() + ".section." + section.key(),
                    fields
            ));
        }
        return new FormRenderModel(definition.key(), definition.objectKey(), definition.version(), sections);
    }

    private static ExpressionModel toExpressionModel(Expression expression) {
        if (expression == null) {
            return null;
        }
        return new ExpressionModel(expression.language(), expression.source());
    }

    String serialize(FormDefinition definition) {
        try {
            return json.writeValueAsString(toPersistable(definition));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize form definition", ex);
        }
    }

    private Map<String, Object> toPersistable(FormDefinition definition) {
        List<Map<String, Object>> sections = new ArrayList<>();
        for (Section section : definition.sections()) {
            List<Map<String, Object>> fields = new ArrayList<>();
            for (Field field : section.fields()) {
                Map<String, Object> fieldMap = new LinkedHashMap<>();
                fieldMap.put("attributeKey", field.attributeKey());
                fieldMap.put("required", field.required());
                fieldMap.put("visibleWhen", expressionMap(field.visibleWhen()));
                fieldMap.put("readOnlyWhen", expressionMap(field.readOnlyWhen()));
                fields.add(fieldMap);
            }
            Map<String, Object> sectionMap = new LinkedHashMap<>();
            sectionMap.put("key", section.key());
            sectionMap.put("labels", section.labels());
            sectionMap.put("fields", fields);
            sections.add(sectionMap);
        }
        return Map.of("sections", sections);
    }

    private static Map<String, String> expressionMap(Expression expression) {
        if (expression == null) {
            return null;
        }
        return Map.of("language", expression.language(), "source", expression.source());
    }
}
