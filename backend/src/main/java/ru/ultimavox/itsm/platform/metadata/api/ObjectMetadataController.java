package ru.ultimavox.itsm.platform.metadata.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.metadata.AttributeDefinition;
import ru.ultimavox.itsm.platform.metadata.ObjectDefinition;
import ru.ultimavox.itsm.platform.metadata.ObjectDefinitionService;
import ru.ultimavox.itsm.platform.metadata.RelationDefinition;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/metadata/objects")
@Tag(name = "Platform — Object Metadata")
class ObjectMetadataController {

    private final ObjectDefinitionService definitions;
    private final AccessControl access;

    ObjectMetadataController(ObjectDefinitionService definitions, AccessControl access) {
        this.definitions = definitions;
        this.access = access;
    }

    @GetMapping
    @Operation(summary = "List active object definitions")
    List<ObjectDefinitionView> list(Authentication authentication) {
        access.require(authentication.getName(), "metadata.read", "metadata", null);
        return definitions.listActive().stream().map(ObjectDefinitionView::from).toList();
    }

    @GetMapping("/{key}")
    @Operation(summary = "Get active object definition by key")
    ObjectDefinitionView get(Authentication authentication, @PathVariable String key) {
        access.require(authentication.getName(), "metadata.read", "metadata", key);
        return definitions.getActiveByKey(key)
                .map(ObjectDefinitionView::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown object key: " + key));
    }

    record ObjectDefinitionView(
            String key,
            int version,
            Map<String, String> labels,
            List<AttributeView> attributes,
            List<RelationView> relations
    ) {
        static ObjectDefinitionView from(ObjectDefinition def) {
            return new ObjectDefinitionView(
                    def.key(),
                    def.version(),
                    def.labels(),
                    def.attributeList().stream().map(AttributeView::from).toList(),
                    def.relations().stream().map(RelationView::from).toList()
            );
        }
    }

    record AttributeView(
            String key,
            String type,
            boolean required,
            boolean searchable,
            Map<String, String> labels,
            List<String> enumValues
    ) {
        static AttributeView from(AttributeDefinition attr) {
            return new AttributeView(
                    attr.key(),
                    attr.type().name(),
                    attr.required(),
                    attr.searchable(),
                    attr.labels(),
                    attr.enumValues()
            );
        }
    }

    record RelationView(
            String key,
            String targetObjectKey,
            String cardinality,
            boolean required,
            Map<String, String> labels
    ) {
        static RelationView from(RelationDefinition rel) {
            return new RelationView(
                    rel.key(),
                    rel.targetObjectKey(),
                    rel.cardinality().name(),
                    rel.required(),
                    rel.labels()
            );
        }
    }
}
