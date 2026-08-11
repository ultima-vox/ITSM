package ru.ultimavox.itsm.platform.notification.api;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ultimavox.itsm.platform.authorization.SelfScopedEndpoint;
import ru.ultimavox.itsm.platform.notification.NotificationPreferenceService;

@RestController
@RequestMapping("/api/v1/me/notification-preferences")
@SelfScopedEndpoint
class NotificationPreferenceController {
    private final NotificationPreferenceService preferences;

    NotificationPreferenceController(NotificationPreferenceService preferences) {
        this.preferences = preferences;
    }

    @GetMapping
    NotificationPreferenceService.Preferences get(Authentication authentication) {
        return preferences.get(authentication.getName());
    }

    @PutMapping
    NotificationPreferenceService.Preferences save(Authentication authentication,
                                                    @RequestBody NotificationPreferenceService.Preferences body) {
        return preferences.save(authentication.getName(), body);
    }
}
