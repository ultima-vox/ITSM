package ru.ultimavox.itsm.platform.sla;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ultimavox.itsm.platform.event.DomainEvent;
import ru.ultimavox.itsm.platform.outbox.IntegrationEventOutbox;
import ru.ultimavox.itsm.platform.sla.SlaPolicy.Target;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Internal SLA service API for business modules.
 * Wires {@link SlaPolicy}, {@link SlaClock} and {@link SlaDeadlineCalculator}
 * for start / pause / resume / breach detection.
 */
@Service
public class SlaService {

    private final SlaPolicyRepository policies;
    private final SlaClockRepository clocks;
    private final SlaDeadlineCalculator calculator;
    private final WorkingCalendarRegistry calendars;
    private final IntegrationEventOutbox outbox;
    private final ObjectMapper json;

    public SlaService(
            SlaPolicyRepository policies,
            SlaClockRepository clocks,
            SlaDeadlineCalculator calculator,
            WorkingCalendarRegistry calendars,
            IntegrationEventOutbox outbox,
            ObjectMapper json
    ) {
        this.policies = policies;
        this.clocks = clocks;
        this.calculator = calculator;
        this.calendars = calendars;
        this.outbox = outbox;
        this.json = json;
    }

    /**
     * Starts SLA clocks for all targets of the policy that match the given context attributes
     * (for example priority=CRITICAL).
     */
    @Transactional
    public List<SlaClock> start(String policyKey, UUID aggregateId, Map<String, String> context, String actorId) {
        SlaPolicy policy = policies.findByKey(policyKey)
                .orElseThrow(() -> new IllegalArgumentException("Unknown SLA policy: " + policyKey));
        WorkingCalendar calendar = calendars.require(policy.calendarKey());
        Instant now = Instant.now();
        List<SlaClock> started = new ArrayList<>();

        for (Target target : policy.targets()) {
            if (!matchesCondition(target.condition(), context)) {
                continue;
            }
            Optional<SlaClock> existing = clocks.findActive(aggregateId, policyKey, target.metric());
            if (existing.isPresent()) {
                continue;
            }

            Instant dueAt = calculator.deadline(now, target.target(), calendar);
            Instant warningAt = target.warningBefore() == null || target.warningBefore().isZero()
                    ? null
                    : dueAt.minus(target.warningBefore());
            // warning must not be before start; if it is, clamp to start
            if (warningAt != null && warningAt.isBefore(now)) {
                warningAt = now;
            }

            SlaClock clock = new SlaClock(
                    UUID.randomUUID(),
                    policyKey,
                    aggregateId,
                    target.metric(),
                    now,
                    dueAt,
                    warningAt,
                    SlaClock.State.RUNNING
            );
            clocks.insert(clock);
            clocks.appendHistory(clock.id(), "START", actorId, writeDetails(Map.of(
                    "policyKey", policyKey,
                    "metric", target.metric(),
                    "dueAt", dueAt.toString()
            )));
            started.add(clock);
        }
        return started;
    }

    @Transactional
    public SlaClock pause(UUID clockId, String actorId) {
        SlaClock clock = requireClock(clockId);
        if (clock.state() != SlaClock.State.RUNNING) {
            throw new IllegalStateException("Only RUNNING clocks can be paused, was " + clock.state());
        }
        SlaClock paused = withState(clock, SlaClock.State.PAUSED);
        clocks.update(paused);
        clocks.appendHistory(clockId, "PAUSE", actorId, "{}");
        return paused;
    }

    /**
     * Pauses all active clocks for the aggregate whose policy lists the given business state as pauseable.
     */
    @Transactional
    public List<SlaClock> pauseForState(UUID aggregateId, String businessState, String actorId) {
        List<SlaClock> paused = new ArrayList<>();
        for (SlaClock clock : clocks.findActiveByAggregate(aggregateId)) {
            if (clock.state() != SlaClock.State.RUNNING) {
                continue;
            }
            Optional<SlaPolicy> policy = policies.findByKey(clock.policyKey());
            if (policy.isPresent() && policy.get().pauseStates().contains(businessState)) {
                paused.add(pause(clock.id(), actorId));
            }
        }
        return paused;
    }

    @Transactional
    public SlaClock resume(UUID clockId, String actorId) {
        SlaClock clock = requireClock(clockId);
        if (clock.state() != SlaClock.State.PAUSED) {
            throw new IllegalStateException("Only PAUSED clocks can be resumed, was " + clock.state());
        }
        // Recalculate remaining business time from now using original target window left.
        // Simplified: keep dueAt as absolute wall; production may recompute remaining minutes.
        SlaClock resumed = withState(clock, SlaClock.State.RUNNING);
        clocks.update(resumed);
        clocks.appendHistory(clockId, "RESUME", actorId, "{}");
        return resumed;
    }

    @Transactional
    public SlaClock achieve(UUID clockId, String actorId) {
        SlaClock clock = requireClock(clockId);
        if (clock.state() == SlaClock.State.ACHIEVED || clock.state() == SlaClock.State.CANCELLED) {
            return clock;
        }
        SlaClock achieved = withState(clock, SlaClock.State.ACHIEVED);
        clocks.update(achieved);
        clocks.appendHistory(clockId, "ACHIEVE", actorId, "{}");
        return achieved;
    }

    @Transactional
    public SlaClock cancel(UUID clockId, String actorId) {
        SlaClock clock = requireClock(clockId);
        SlaClock cancelled = withState(clock, SlaClock.State.CANCELLED);
        clocks.update(cancelled);
        clocks.appendHistory(clockId, "CANCEL", actorId, "{}");
        return cancelled;
    }

    /**
     * Detects breaches for RUNNING clocks past due_at; emits domain events via outbox.
     *
     * @return number of clocks marked BREACHED
     */
    @Transactional
    public int detectBreaches(int batchSize) {
        Instant now = Instant.now();
        int count = 0;
        for (SlaClock clock : clocks.findDueRunning(batchSize)) {
            if (clock.dueAt().isAfter(now)) {
                continue;
            }
            SlaClock breached = withState(clock, SlaClock.State.BREACHED);
            clocks.update(breached);
            clocks.appendHistory(clock.id(), "BREACH", "system", writeDetails(Map.of(
                    "dueAt", clock.dueAt().toString(),
                    "detectedAt", now.toString()
            )));
            outbox.record(new DomainEvent(
                    UUID.randomUUID(),
                    "sla.breached",
                    1,
                    now,
                    UUID.randomUUID(),
                    "sla-clock",
                    clock.id().toString(),
                    Map.of(
                            "policyKey", clock.policyKey(),
                            "aggregateId", clock.aggregateId().toString(),
                            "metric", clock.metric(),
                            "dueAt", clock.dueAt().toString()
                    )
            ));
            count++;
        }
        return count;
    }

    @Transactional(readOnly = true)
    public List<SlaClock> activeClocks(UUID aggregateId) {
        return clocks.findActiveByAggregate(aggregateId);
    }

    @Transactional(readOnly = true)
    public Optional<SlaPolicy> findPolicy(String policyKey) {
        return policies.findByKey(policyKey);
    }

    private SlaClock requireClock(UUID clockId) {
        return clocks.findById(clockId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown SLA clock: " + clockId));
    }

    private static SlaClock withState(SlaClock clock, SlaClock.State state) {
        return new SlaClock(
                clock.id(),
                clock.policyKey(),
                clock.aggregateId(),
                clock.metric(),
                clock.startedAt(),
                clock.dueAt(),
                clock.warningAt(),
                state
        );
    }

    /**
     * Condition format: empty (always match) or {@code key=value} equality against context.
     */
    static boolean matchesCondition(String condition, Map<String, String> context) {
        if (condition == null || condition.isBlank()) {
            return true;
        }
        int eq = condition.indexOf('=');
        if (eq <= 0) {
            return true;
        }
        String key = condition.substring(0, eq).trim();
        String expected = condition.substring(eq + 1).trim();
        String actual = context == null ? null : context.get(key);
        return expected.equals(actual);
    }

    private String writeDetails(Map<String, Object> details) {
        try {
            return json.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
