package ru.ultimavox.itsm.platform.forms.api;

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
import ru.ultimavox.itsm.platform.forms.FormDefinitionService;
import ru.ultimavox.itsm.platform.forms.FormRenderModel;

@RestController
@RequestMapping("/api/v1/metadata/forms")
@Tag(name = "Platform — Form Metadata")
class FormMetadataController {

    private final FormDefinitionService forms;
    private final AccessControl access;

    FormMetadataController(FormDefinitionService forms, AccessControl access) {
        this.forms = forms;
        this.access = access;
    }

    @GetMapping("/{objectKey}")
    @Operation(summary = "Get active form render model for an object type")
    FormRenderModel get(Authentication authentication, @PathVariable String objectKey) {
        access.require(authentication.getName(), "metadata.read", "metadata", objectKey);
        return forms.renderForObject(objectKey)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No form definition for object: " + objectKey));
    }
}
