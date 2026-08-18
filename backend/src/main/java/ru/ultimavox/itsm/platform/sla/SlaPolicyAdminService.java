package ru.ultimavox.itsm.platform.sla;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.ultimavox.itsm.platform.audit.AuditTrail;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.platform.sla.SlaPolicyRepository.SlaPolicyView;

@Service
public class SlaPolicyAdminService {
    private final SlaPolicyRepository repository;
    private final AuditTrail audit;
    private final IntegrationEventOutbox outbox;

    public SlaPolicyAdminService(SlaPolicyRepository repository, AuditTrail audit, IntegrationEventOutbox outbox) {
        this.repository = repository;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional
    public SlaPolicyView update(String actor, UUID id, int expectedVersion, Boolean enabled,
                                List<SlaPolicy.Target> targets) {
        if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion must be positive");
        SlaPolicyView saved = repository.update(id, expectedVersion, enabled, targets)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "SLA policy changed or does not exist"));
        Instant now = Instant.now();
        UUID correlation = UUID.randomUUID();
        Map<String, Object> details = Map.of("version", saved.version(), "enabled", saved.enabled());
        audit.append(new AuditTrail.Entry(actor, "sla.policy-updated", "sla_policy", saved.policy().key(),
                Map.of(), details, correlation, now));
        outbox.record(new DomainEvent(UUID.randomUUID(), "sla.policy-updated", 1, now, correlation,
                "sla_policy", saved.policy().key(), details));
        return saved;
    }

    @Transactional
    public SlaPolicyView create(String actor, String policyKey, String calendarKey,
                                List<SlaPolicy.Target> targets, java.util.Set<String> pauseStates) {
        SlaPolicyView saved = repository.create(policyKey, calendarKey, targets, pauseStates);
        Instant now = Instant.now();
        UUID correlation = UUID.randomUUID();
        Map<String, Object> details = Map.of("policyKey", policyKey, "calendarKey",
                calendarKey == null ? "default-business" : calendarKey);
        audit.append(new AuditTrail.Entry(actor, "sla.policy-created", "sla_policy", policyKey,
                Map.of(), details, correlation, now));
        outbox.record(new DomainEvent(UUID.randomUUID(), "sla.policy-created", 1, now, correlation,
                "sla_policy", policyKey, details));
        return saved;
    }

    @Transactional
    public boolean delete(String actor, UUID id, String policyKey) {
        boolean deleted = repository.delete(id);
        if (deleted) {
            Instant now = Instant.now();
            UUID correlation = UUID.randomUUID();
            audit.append(new AuditTrail.Entry(actor, "sla.policy-deleted", "sla_policy", policyKey,
                    Map.of("policyKey", policyKey), Map.of(), correlation, now));
            outbox.record(new DomainEvent(UUID.randomUUID(), "sla.policy-deleted", 1, now, correlation,
                    "sla_policy", policyKey, Map.of("policyKey", policyKey)));
        }
        return deleted;
    }
}
