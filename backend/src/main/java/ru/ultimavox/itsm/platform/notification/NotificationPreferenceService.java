package ru.ultimavox.itsm.platform.notification;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;

@Service
public class NotificationPreferenceService {
    private static final Preferences DEFAULTS = new Preferences(true, false, true, true, true);
    private final JdbcTemplate jdbc;
    private final AuditTrail audit;
    private final IntegrationEventOutbox outbox;

    public NotificationPreferenceService(JdbcTemplate jdbc, AuditTrail audit, IntegrationEventOutbox outbox) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public Preferences get(String subject) {
        if (subject == null || subject.isBlank()) throw new IllegalArgumentException("subject is required");
        return jdbc.query("""
                SELECT email_enabled, desktop_enabled, sla_alerts_enabled,
                       assignment_enabled, mentions_enabled
                FROM notification_preference WHERE org_id = ? AND subject_id = ?
                """, (rs, i) -> new Preferences(rs.getBoolean(1), rs.getBoolean(2), rs.getBoolean(3),
                rs.getBoolean(4), rs.getBoolean(5)), OrganizationContext.current(), subject)
                .stream().findFirst().orElse(DEFAULTS);
    }

    @Transactional
    public Preferences save(String subject, Preferences preferences) {
        if (subject == null || subject.isBlank()) throw new IllegalArgumentException("subject is required");
        if (preferences == null) throw new IllegalArgumentException("preferences are required");
        Preferences before = get(subject);
        jdbc.update("""
                INSERT INTO notification_preference(org_id, subject_id, email_enabled, desktop_enabled,
                  sla_alerts_enabled, assignment_enabled, mentions_enabled, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (org_id, subject_id) DO UPDATE SET
                  email_enabled = EXCLUDED.email_enabled, desktop_enabled = EXCLUDED.desktop_enabled,
                  sla_alerts_enabled = EXCLUDED.sla_alerts_enabled,
                  assignment_enabled = EXCLUDED.assignment_enabled,
                  mentions_enabled = EXCLUDED.mentions_enabled, updated_at = now()
                """, OrganizationContext.current(), subject, preferences.email(), preferences.desktop(),
                preferences.slaAlerts(), preferences.assignment(), preferences.mentions());
        Instant now = Instant.now();
        UUID correlation = UUID.randomUUID();
        audit.append(new AuditTrail.Entry(subject, "notification.preferences-updated",
                "notification_preference", subject, asMap(before), asMap(preferences), correlation, now));
        outbox.record(new DomainEvent(UUID.randomUUID(), "notification.preferences-updated", 1, now,
                correlation, "notification_preference", subject, asMap(preferences)));
        return preferences;
    }

    @Transactional(readOnly = true)
    public boolean allows(NotificationRequest request) {
        Preferences value = get(request.recipientSubject());
        if (request.channel() == NotificationRequest.Channel.EMAIL && !value.email()) return false;
        String template = request.templateKey() == null ? "" : request.templateKey().toLowerCase(java.util.Locale.ROOT);
        if ((template.contains("sla") || template.contains("breach")) && !value.slaAlerts()) return false;
        if ((template.contains("assign") || template.contains("owner")) && !value.assignment()) return false;
        return !template.contains("mention") || value.mentions();
    }

    private static Map<String, Object> asMap(Preferences value) {
        return Map.of("email", value.email(), "desktop", value.desktop(), "slaAlerts", value.slaAlerts(),
                "assignment", value.assignment(), "mentions", value.mentions());
    }

    public record Preferences(boolean email, boolean desktop, boolean slaAlerts,
                              boolean assignment, boolean mentions) {}
}
