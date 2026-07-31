package ru.ultimavox.itsm.platform.localization.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.localization.TranslationAdminService;
import ru.ultimavox.itsm.platform.localization.TranslationAdminService.TranslationEntry;

@RestController
@RequestMapping("/api/v1/admin/translations")
@Tag(name = "Platform — Translation Admin")
class TranslationAdminController {

  private final TranslationAdminService translations;
  private final AccessControl access;

  TranslationAdminController(TranslationAdminService translations, AccessControl access) {
    this.translations = translations;
    this.access = access;
  }

  @GetMapping
  @Operation(summary = "List translations (optional namespace/locale filters)")
  List<TranslationView> list(
      Authentication authentication,
      @RequestParam(required = false) String namespace,
      @RequestParam(required = false) String locale
  ) {
    requireTranslationAdmin(authentication.getName());
    return translations.list(namespace, locale).stream().map(TranslationView::from).toList();
  }

  @PutMapping
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "Upsert a translation key for a locale")
  TranslationView upsert(Authentication authentication, @Valid @RequestBody UpsertTranslation body) {
    requireTranslationAdmin(authentication.getName());
    try {
      return TranslationView.from(
          translations.upsert(body.namespace(), body.key(), body.locale(), body.value())
      );
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  /**
   * Accepts either dedicated {@code admin.translations} or broader {@code metadata.write}.
   */
  private void requireTranslationAdmin(String subject) {
    if (access.isAllowed(subject, "admin.translations", "translation", null)
        || access.isAllowed(subject, "metadata.write", "translation", null)
        || access.isAllowed(subject, "admin.full", "translation", null)) {
      return;
    }
    // Use require so AccessDeniedException is thrown with a clear permission name
    access.require(subject, "admin.translations", "translation", null);
  }

  record TranslationView(
      UUID id,
      String namespace,
      String key,
      String locale,
      String value,
      int version,
      Instant updatedAt
  ) {
    static TranslationView from(TranslationEntry e) {
      return new TranslationView(
          e.id(), e.namespace(), e.key(), e.locale(), e.value(), e.version(), e.updatedAt()
      );
    }
  }

  record UpsertTranslation(
      @NotBlank String namespace,
      @NotBlank String key,
      @NotBlank String locale,
      @NotBlank String value
  ) {}
}
