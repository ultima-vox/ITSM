package ru.ultimavox.itsm.platform.forms;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.metadata.ObjectDefinition;
import ru.ultimavox.itsm.platform.metadata.ObjectDefinitionService;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

/** Immutable tenant form drafts with validated, atomic publication. */
@Service
public class FormDefinitionAdminService {
    private static final Set<String> REQUIRED_LOCALES = Set.of("ru", "en");
    private final FormDefinitionRepository repository;
    private final FormDefinitionService forms;
    private final ObjectDefinitionService objects;
    private final AuditTrail audit;
    private final IntegrationEventOutbox outbox;

    public FormDefinitionAdminService(FormDefinitionRepository repository, FormDefinitionService forms,
                                      ObjectDefinitionService objects, AuditTrail audit,
                                      IntegrationEventOutbox outbox) {
        this.repository = repository;
        this.forms = forms;
        this.objects = objects;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public List<FormDefinitionVersion> versions(String key) {
        validateKey(key, "form");
        return repository.findVersions(key);
    }

    @Transactional
    public FormDefinition createDraft(String actor, Draft command) {
        if (command == null) throw new IllegalArgumentException("Form body is required");
        validateKey(command.key(), "form");
        validateKey(command.objectKey(), "object");
        ObjectDefinition object = objects.requireActiveByKey(command.objectKey());
        List<FormDefinition.Section> sections = command.sections() == null ? List.of() : List.copyOf(command.sections());
        validateSections(sections, object);
        FormDefinition draft = new FormDefinition(UUID.randomUUID(), command.key(), command.objectKey(), 1, sections);
        FormDefinition saved = repository.insertNextDraft(draft, forms.serialize(draft));
        record(actor, "metadata.form-draft-created", saved);
        return saved;
    }

    @Transactional
    public FormDefinition publish(String actor, String key, int version) {
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        FormDefinitionVersion candidate = repository.findVersions(key).stream()
                .filter(item -> item.definition().version() == version).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown form version: " + key + " v" + version));
        if (candidate.active()) return candidate.definition();
        ObjectDefinition object = objects.requireActiveByKey(candidate.definition().objectKey());
        validateSections(candidate.definition().sections(), object);
        FormDefinition published = repository.publish(key, version);
        record(actor, "metadata.form-published", published);
        return published;
    }

    private void validateSections(List<FormDefinition.Section> sections, ObjectDefinition object) {
        if (sections.isEmpty() || sections.size() > 50) throw new IllegalArgumentException("Form requires 1 to 50 sections");
        Set<String> sectionKeys = new HashSet<>();
        Set<String> fieldKeys = new HashSet<>();
        for (FormDefinition.Section section : sections) {
            validateKey(section.key(), "section");
            validateLabels(section.labels(), section.key());
            if (!sectionKeys.add(section.key())) throw new IllegalArgumentException("Duplicate section: " + section.key());
            if (section.fields().isEmpty() || section.fields().size() > 100) {
                throw new IllegalArgumentException("Section requires 1 to 100 fields: " + section.key());
            }
            for (FormDefinition.Field field : section.fields()) {
                validateKey(field.attributeKey(), "attribute");
                if (!object.attributes().containsKey(field.attributeKey())) {
                    throw new IllegalArgumentException("Unknown object attribute: " + field.attributeKey());
                }
                if (!fieldKeys.add(field.attributeKey())) throw new IllegalArgumentException("Duplicate form field: " + field.attributeKey());
                validateExpression(field.visibleWhen());
                validateExpression(field.readOnlyWhen());
            }
        }
    }

    private void validateExpression(FormDefinition.Expression expression) {
        if (expression != null && expression.source().length() > 2_000) {
            throw new IllegalArgumentException("CEL expression exceeds 2000 characters");
        }
    }

    private void validateLabels(Map<String, String> labels, String owner) {
        if (labels == null || !labels.keySet().containsAll(REQUIRED_LOCALES)
                || REQUIRED_LOCALES.stream().anyMatch(locale -> labels.get(locale) == null || labels.get(locale).isBlank())) {
            throw new IllegalArgumentException(owner + " requires non-blank ru and en labels");
        }
    }

    private void validateKey(String key, String kind) {
        if (key == null || !key.matches("[a-z][a-z0-9.-]{0,99}")) {
            throw new IllegalArgumentException("Invalid " + kind + " key: " + key);
        }
    }

    private void record(String actor, String action, FormDefinition definition) {
        Instant now = Instant.now();
        UUID correlation = UUID.randomUUID();
        Map<String, Object> detail = Map.of("version", definition.version(), "objectKey", definition.objectKey());
        audit.append(new AuditTrail.Entry(actor, action, "form_definition", definition.key(), Map.of(), detail, correlation, now));
        outbox.record(new DomainEvent(UUID.randomUUID(), action, 1, now, correlation,
                "form_definition", definition.key(), detail));
    }

    public record Draft(String key, String objectKey, List<FormDefinition.Section> sections) {}
}
