package ru.ultimavox.itsm.platform.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.ultimavox.itsm.platform.metadata.AttributeDefinition.AttributeType;

class ObjectDefinitionServiceTest {

    private ObjectDefinitionService service;
    private ObjectDefinition workItem;

    @BeforeEach
    void setUp() {
        Map<String, AttributeDefinition> attributes = new LinkedHashMap<>();
        attributes.put("title", new AttributeDefinition(
                "title", AttributeType.TEXT, true, true, Map.of("en", "Title"), List.of()));
        attributes.put("description", new AttributeDefinition(
                "description", AttributeType.RICH_TEXT, true, true, Map.of(), List.of()));
        attributes.put("priority", new AttributeDefinition(
                "priority", AttributeType.ENUM, true, true, Map.of(),
                List.of("CRITICAL", "HIGH", "MEDIUM", "LOW")));
        attributes.put("type", new AttributeDefinition(
                "type", AttributeType.ENUM, true, true, Map.of(),
                List.of("INCIDENT", "SERVICE_REQUEST")));
        attributes.put("service", new AttributeDefinition(
                "service", AttributeType.TEXT, true, true, Map.of(), List.of()));
        attributes.put("state", new AttributeDefinition(
                "state", AttributeType.ENUM, true, false, Map.of(),
                List.of("NEW", "IN_PROGRESS", "CLOSED")));
        attributes.put("requester_id", new AttributeDefinition(
                "requester_id", AttributeType.USER, true, false, Map.of(), List.of()));
        attributes.put("assignee_id", new AttributeDefinition(
                "assignee_id", AttributeType.USER, false, true, Map.of(), List.of()));
        attributes.put("impact", new AttributeDefinition(
                "impact", AttributeType.NUMBER, false, false, Map.of(), List.of()));
        attributes.put("urgent", new AttributeDefinition(
                "urgent", AttributeType.BOOLEAN, false, false, Map.of(), List.of()));

        workItem = new ObjectDefinition(
                UUID.randomUUID(),
                "work-item",
                1,
                Map.of("en", "Work Item", "ru", "Рабочий элемент"),
                attributes,
                List.of(new RelationDefinition(
                        "related_ci",
                        "configuration-item",
                        RelationDefinition.Cardinality.MANY_TO_MANY,
                        false,
                        Map.of("en", "Related CIs")
                ))
        );

        service = new ObjectDefinitionService(new FixedRepository(workItem));
    }

    @Test
    void getActiveByKey_returns_seeded_definition() {
        Optional<ObjectDefinition> found = service.getActiveByKey("work-item");
        assertThat(found).isPresent();
        assertThat(found.orElseThrow().attributes()).containsKey("title");
        assertThat(found.orElseThrow().relations()).hasSize(1);
    }

    @Test
    void listActive_returns_all() {
        assertThat(service.listActive()).extracting(ObjectDefinition::key).containsExactly("work-item");
    }

    @Test
    void validateInstance_accepts_valid_payload() {
        service.validateInstance(workItem, Map.of(
                "title", "VPN down",
                "description", "Users cannot connect",
                "priority", "HIGH",
                "type", "INCIDENT",
                "service", "Workplace",
                "state", "NEW",
                "requester_id", "user-1",
                "assignee_id", "agent-2",
                "impact", 3,
                "urgent", true
        ));
    }

    @Test
    void validateInstance_rejects_missing_required_fields() {
        assertThatThrownBy(() -> service.validateInstance(workItem, Map.of(
                "title", "Only title"
        ))).isInstanceOf(ObjectValidationException.class)
                .satisfies(ex -> {
                    ObjectValidationException ove = (ObjectValidationException) ex;
                    assertThat(ove.errors()).anyMatch(e -> e.contains("description"));
                    assertThat(ove.errors()).anyMatch(e -> e.contains("priority"));
                    assertThat(ove.errors()).anyMatch(e -> e.contains("requester_id"));
                });
    }

    @Test
    void validateInstance_rejects_invalid_enum_value() {
        assertThatThrownBy(() -> service.validateInstance(workItem, Map.of(
                "title", "x",
                "description", "y",
                "priority", "ULTRA",
                "type", "INCIDENT",
                "service", "s",
                "state", "NEW",
                "requester_id", "u1"
        ))).isInstanceOf(ObjectValidationException.class)
                .hasMessageContaining("priority");
    }

    @Test
    void validateInstance_rejects_wrong_types() {
        assertThatThrownBy(() -> service.validateInstance(workItem, Map.of(
                "title", 42,
                "description", "y",
                "priority", "HIGH",
                "type", "INCIDENT",
                "service", "s",
                "state", "NEW",
                "requester_id", "u1"
        ))).isInstanceOf(ObjectValidationException.class)
                .hasMessageContaining("title");
    }

    @Test
    void validateInstance_rejects_unknown_attributes() {
        assertThatThrownBy(() -> service.validateInstance(workItem, Map.of(
                "title", "x",
                "description", "y",
                "priority", "HIGH",
                "type", "INCIDENT",
                "service", "s",
                "state", "NEW",
                "requester_id", "u1",
                "hacked_field", "nope"
        ))).isInstanceOf(ObjectValidationException.class)
                .hasMessageContaining("hacked_field");
    }

    @Test
    void requireActiveByKey_throws_when_missing() {
        assertThatThrownBy(() -> service.requireActiveByKey("unknown"))
                .isInstanceOf(ObjectDefinitionNotFoundException.class);
    }

    private static final class FixedRepository implements ObjectDefinitionRepository {
        private final ObjectDefinition definition;

        FixedRepository(ObjectDefinition definition) {
            this.definition = definition;
        }

        @Override
        public Optional<ObjectDefinition> findActiveByKey(String objectKey) {
            return definition.key().equals(objectKey) ? Optional.of(definition) : Optional.empty();
        }

        @Override
        public List<ObjectDefinition> findAllActive() {
            return List.of(definition);
        }

        @Override
        public Optional<ObjectDefinition> findByKeyAndVersion(String objectKey, int version) {
            if (definition.key().equals(objectKey) && definition.version() == version) {
                return Optional.of(definition);
            }
            return Optional.empty();
        }
    }
}
