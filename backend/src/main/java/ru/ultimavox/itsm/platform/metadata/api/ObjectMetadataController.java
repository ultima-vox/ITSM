package ru.ultimavox.itsm.platform.metadata.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.metadata.AttributeDefinition;
import ru.ultimavox.itsm.platform.metadata.ObjectDefinition;
import ru.ultimavox.itsm.platform.metadata.ObjectDefinitionService;
import ru.ultimavox.itsm.platform.metadata.ObjectDefinitionAdminService;
import ru.ultimavox.itsm.platform.metadata.ObjectDefinitionVersion;
import ru.ultimavox.itsm.platform.metadata.RelationDefinition;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/metadata/objects")
@Tag(name = "Platform — Object Metadata")
class ObjectMetadataController {

    private final ObjectDefinitionService definitions;
    private final AccessControl access;
    private final ObjectDefinitionAdminService admin;

    ObjectMetadataController(ObjectDefinitionService definitions, AccessControl access,
                             ObjectDefinitionAdminService admin) {
        this.definitions = definitions;
        this.access = access;
        this.admin = admin;
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

    @GetMapping("/{key}/versions")
    @Operation(summary = "List tenant-owned draft and published object schema versions")
    List<ObjectDefinitionVersionView> versions(Authentication authentication, @PathVariable String key) {
        access.require(authentication.getName(), "metadata.write", "metadata", key);
        return admin.versions(key).stream().map(ObjectDefinitionVersionView::from).toList();
    }

    @PostMapping("/drafts")
    @Operation(summary = "Create the next immutable object schema draft")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    ObjectDefinitionVersionView createDraft(Authentication authentication, @RequestBody DraftRequest body) {
        String actor = authentication.getName();
        access.require(actor, "metadata.write", "metadata", body.key());
        try {
            ObjectDefinition saved = admin.createDraft(actor, new ObjectDefinitionAdminService.Draft(
                    body.key(), body.labels(), body.attributes(), body.relations()));
            return new ObjectDefinitionVersionView(ObjectDefinitionView.from(saved), false);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/{key}/versions/{version}/publish")
    @Operation(summary = "Atomically publish a compatible tenant object schema version")
    ObjectDefinitionVersionView publish(Authentication authentication, @PathVariable String key,
                                        @PathVariable int version) {
        String actor = authentication.getName();
        access.require(actor, "metadata.write", "metadata", key);
        try {
            return new ObjectDefinitionVersionView(
                    ObjectDefinitionView.from(admin.publish(actor, key, version)), true);
        } catch (ru.ultimavox.itsm.platform.metadata.ObjectDefinitionNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }

    record DraftRequest(String key, Map<String, String> labels,
                        List<AttributeDefinition> attributes, List<RelationDefinition> relations) {}

    record ObjectDefinitionVersionView(ObjectDefinitionView definition, boolean active) {
        static ObjectDefinitionVersionView from(ObjectDefinitionVersion version) {
            return new ObjectDefinitionVersionView(
                    ObjectDefinitionView.from(version.definition()), version.active());
        }
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
