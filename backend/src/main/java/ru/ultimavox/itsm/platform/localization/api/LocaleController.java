package ru.ultimavox.itsm.platform.localization.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.ultimavox.itsm.platform.localization.LocalePreferenceService;
import ru.ultimavox.itsm.platform.authorization.SelfScopedEndpoint;

@RestController
@RequestMapping("/api/v1/me/locale")
@SelfScopedEndpoint
@Tag(name = "Platform — Locale")
class LocaleController {

  private final LocalePreferenceService service;

  LocaleController(LocalePreferenceService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "Get current user locale preference and supported locales")
  LocaleView get(Authentication authentication) {
    return new LocaleView(
        service.preferenceFor(authentication.getName()),
        service.supportedLocales()
    );
  }

  @PutMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Update current user interface locale")
  void put(Authentication authentication, @Valid @RequestBody UpdateLocale request) {
    service.changePreference(authentication.getName(), request.locale());
  }

  record LocaleView(String locale, Set<String> supportedLocales) {}

  record UpdateLocale(
      @Pattern(regexp = "^[a-z]{2,3}(-[A-Z]{2})?$") String locale
  ) {}
}
