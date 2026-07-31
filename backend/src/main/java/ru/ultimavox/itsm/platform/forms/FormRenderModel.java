package ru.ultimavox.itsm.platform.forms;

import java.util.List;
import java.util.Map;

/**
 * Client-ready form model: sections, fields, visibility rules and localization keys.
 * Does not evaluate CEL at render time — the UI (or a future CEL evaluator) does.
 */
public record FormRenderModel(
        String formKey,
        String objectKey,
        int version,
        List<SectionModel> sections
) {
    public record SectionModel(
            String key,
            Map<String, String> labels,
            String localizationKey,
            List<FieldModel> fields
    ) {}

    public record FieldModel(
            String attributeKey,
            boolean required,
            String localizationKey,
            ExpressionModel visibleWhen,
            ExpressionModel readOnlyWhen
    ) {}

    public record ExpressionModel(String language, String source) {}
}
