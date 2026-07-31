package ru.ultimavox.itsm.platform.metadata;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.metadata.AttributeDefinition.AttributeType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Application service for metadata object definitions: lookup and instance payload validation.
 */
@Service
public class ObjectDefinitionService {

    private final ObjectDefinitionRepository repository;

    public ObjectDefinitionService(ObjectDefinitionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<ObjectDefinition> getActiveByKey(String objectKey) {
        return repository.findActiveByKey(objectKey);
    }

    @Transactional(readOnly = true)
    public ObjectDefinition requireActiveByKey(String objectKey) {
        return repository.findActiveByKey(objectKey)
                .orElseThrow(() -> new ObjectDefinitionNotFoundException(objectKey));
    }

    @Transactional(readOnly = true)
    public List<ObjectDefinition> listActive() {
        return repository.findAllActive();
    }

    /**
     * Validates an instance payload against definition rules (required fields and value types).
     * Unknown attributes are reported as errors to prevent silent schema drift.
     */
    public void validateInstance(ObjectDefinition definition, Map<String, Object> payload) {
        List<String> errors = new ArrayList<>();
        Map<String, Object> data = payload == null ? Map.of() : payload;

        for (AttributeDefinition attribute : definition.attributes().values()) {
            Object value = data.get(attribute.key());
            boolean missing = value == null || (value instanceof String s && s.isBlank());
            if (attribute.required() && missing) {
                errors.add("Required attribute '%s' is missing".formatted(attribute.key()));
                continue;
            }
            if (missing) {
                continue;
            }
            validateType(attribute, value, errors);
        }

        for (String key : data.keySet()) {
            if (!definition.attributes().containsKey(key)) {
                errors.add("Unknown attribute '%s' for object '%s'".formatted(key, definition.key()));
            }
        }

        if (!errors.isEmpty()) {
            throw new ObjectValidationException(errors);
        }
    }

    /**
     * Validates using the active definition for the given object key.
     */
    @Transactional(readOnly = true)
    public void validateInstance(String objectKey, Map<String, Object> payload) {
        validateInstance(requireActiveByKey(objectKey), payload);
    }

    private void validateType(AttributeDefinition attribute, Object value, List<String> errors) {
        AttributeType type = attribute.type();
        switch (type) {
            case TEXT, RICH_TEXT, USER, REFERENCE -> {
                if (!(value instanceof String)) {
                    errors.add("Attribute '%s' expects a string".formatted(attribute.key()));
                }
            }
            case NUMBER -> {
                if (!(value instanceof Number) && !isNumericString(value)) {
                    errors.add("Attribute '%s' expects a number".formatted(attribute.key()));
                }
            }
            case BOOLEAN -> {
                if (!(value instanceof Boolean) && !(value instanceof String s && ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)))) {
                    errors.add("Attribute '%s' expects a boolean".formatted(attribute.key()));
                }
            }
            case DATE_TIME -> {
                if (!(value instanceof Instant) && !(value instanceof String s && isInstant(s))) {
                    errors.add("Attribute '%s' expects an ISO-8601 date-time".formatted(attribute.key()));
                }
            }
            case ENUM -> {
                String asText = String.valueOf(value);
                if (!attribute.enumValues().contains(asText)) {
                    errors.add("Attribute '%s' value '%s' is not in %s"
                            .formatted(attribute.key(), asText, attribute.enumValues()));
                }
            }
            case ATTACHMENT -> {
                // attachment payloads are opaque identifiers or maps; accept String or Map
                if (!(value instanceof String) && !(value instanceof Map<?, ?>)) {
                    errors.add("Attribute '%s' expects an attachment reference".formatted(attribute.key()));
                }
            }
        }
    }

    private static boolean isNumericString(Object value) {
        if (!(value instanceof String s) || s.isBlank()) {
            return false;
        }
        try {
            new BigDecimal(s);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean isInstant(String value) {
        try {
            Instant.parse(value);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
