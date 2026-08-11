package ru.ultimavox.itsm.platform.forms.api;

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
import ru.ultimavox.itsm.platform.forms.FormDefinitionService;
import ru.ultimavox.itsm.platform.forms.FormDefinition;
import ru.ultimavox.itsm.platform.forms.FormDefinitionAdminService;
import ru.ultimavox.itsm.platform.forms.FormDefinitionVersion;
import ru.ultimavox.itsm.platform.forms.FormRenderModel;
import java.util.List;

@RestController
@RequestMapping("/api/v1/metadata/forms")
@Tag(name = "Platform — Form Metadata")
class FormMetadataController {

    private final FormDefinitionService forms;
    private final FormDefinitionAdminService admin;
    private final AccessControl access;

    FormMetadataController(FormDefinitionService forms, FormDefinitionAdminService admin, AccessControl access) {
        this.forms = forms;
        this.admin = admin;
        this.access = access;
    }

    @GetMapping({"/{objectKey}", "/by-object/{objectKey}"})
    @Operation(summary = "Get active form render model for an object type")
    FormRenderModel get(Authentication authentication, @PathVariable String objectKey) {
        access.require(authentication.getName(), "metadata.read", "metadata", objectKey);
        return forms.renderForObject(objectKey)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No form definition for object: " + objectKey));
    }

    @GetMapping("/definitions/{formKey}/versions")
    List<FormDefinitionVersion> versions(Authentication authentication, @PathVariable String formKey) {
        access.require(authentication.getName(), "metadata.write", "form_definition", formKey);
        return admin.versions(formKey);
    }

    @PostMapping("/drafts")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    FormDefinitionVersion createDraft(Authentication authentication,
                                      @RequestBody FormDefinitionAdminService.Draft request) {
        access.require(authentication.getName(), "metadata.write", "form_definition", request.key());
        return new FormDefinitionVersion(admin.createDraft(authentication.getName(), request), false);
    }

    @PostMapping("/definitions/{formKey}/versions/{version}/publish")
    FormDefinitionVersion publish(Authentication authentication, @PathVariable String formKey,
                                  @PathVariable int version) {
        access.require(authentication.getName(), "metadata.write", "form_definition", formKey);
        return new FormDefinitionVersion(admin.publish(authentication.getName(), formKey, version), true);
    }
}
