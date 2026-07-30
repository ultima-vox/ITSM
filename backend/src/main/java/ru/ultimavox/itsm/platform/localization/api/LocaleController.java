package ru.ultimavox.itsm.platform.localization.api;
import jakarta.validation.Valid; import jakarta.validation.constraints.Pattern; import java.util.Set; import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import ru.ultimavox.itsm.platform.localization.LocalePreferenceService;
@RestController @RequestMapping("/api/v1/me/locale") class LocaleController {
 private final LocalePreferenceService service; LocaleController(LocalePreferenceService service) { this.service=service; }
 @GetMapping public LocaleView get(@RequestHeader(value="X-Subject", defaultValue="demo") String subject) { return new LocaleView(service.preferenceFor(subject),service.supportedLocales()); }
 @PutMapping @ResponseStatus(HttpStatus.NO_CONTENT) public void put(@RequestHeader(value="X-Subject", defaultValue="demo") String subject,@Valid @RequestBody UpdateLocale request) { service.changePreference(subject,request.locale()); }
 record LocaleView(String locale, Set<String> supportedLocales) {} record UpdateLocale(@Pattern(regexp="^[a-z]{2,3}(-[A-Z]{2})?$") String locale) {}
}
