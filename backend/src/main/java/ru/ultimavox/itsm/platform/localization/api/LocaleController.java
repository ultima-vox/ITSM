package ru.ultimavox.itsm.platform.localization.api;
import jakarta.validation.Valid; import org.springframework.security.core.Authentication; import jakarta.validation.constraints.Pattern; import java.util.Set; import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import ru.ultimavox.itsm.platform.localization.LocalePreferenceService;
@RestController @RequestMapping("/api/v1/me/locale") class LocaleController {
 private final LocalePreferenceService service; LocaleController(LocalePreferenceService service) { this.service=service; }
 @GetMapping public LocaleView get(Authentication authentication) { return new LocaleView(service.preferenceFor(authentication.getName()),service.supportedLocales()); }
 @PutMapping @ResponseStatus(HttpStatus.NO_CONTENT) public void put(Authentication authentication,@Valid @RequestBody UpdateLocale request) { service.changePreference(authentication.getName(),request.locale()); }
 record LocaleView(String locale, Set<String> supportedLocales) {} record UpdateLocale(@Pattern(regexp="^[a-z]{2,3}(-[A-Z]{2})?$") String locale) {}
}
