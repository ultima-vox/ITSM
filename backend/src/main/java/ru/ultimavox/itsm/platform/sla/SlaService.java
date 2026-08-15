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
                    null,
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
        Instant now = Instant.now();
        SlaClock paused = withTiming(
                clock, clock.dueAt(), clock.warningAt(), now, SlaClock.State.PAUSED);
        clocks.update(paused);
        clocks.appendHistory(clockId, "PAUSE", actorId, "{}");
        return paused;
    }

    /**
     * True when any active clock of the aggregate has a policy that lists the given business state
     * as pauseable. Used by callers to decide whether a transition pauses or resumes clocks.
     */
    @Transactional(readOnly = true)
    public boolean isPauseable(UUID aggregateId, String businessState) {
        for (SlaClock clock : clocks.findActiveByAggregate(aggregateId)) {
            Optional<SlaPolicy> policy = policies.findByKey(clock.policyKey());
            if (policy.isPresent() && policy.get().pauseStates().contains(businessState)) {
                return true;
            }
        }
        return false;
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
        Instant now = Instant.now();
        SlaPolicy policy = policies.findByKey(clock.policyKey())
                .orElseThrow(() -> new IllegalArgumentException("Unknown SLA policy: " + clock.policyKey()));
        WorkingCalendar calendar = calendars.require(policy.calendarKey());
        Instant pausedAt = clock.pausedAt();
        Instant dueAt = calculator.deadline(
                now, calculator.businessDuration(pausedAt, clock.dueAt(), calendar), calendar);
        Instant warningAt = clock.warningAt();
        if (warningAt != null && warningAt.isAfter(pausedAt)) {
            warningAt = calculator.deadline(
                    now, calculator.businessDuration(pausedAt, warningAt, calendar), calendar);
        }
        SlaClock resumed = withTiming(clock, dueAt, warningAt, null, SlaClock.State.RUNNING);
        clocks.update(resumed);
        clocks.appendHistory(clockId, "RESUME", actorId, writeDetails(Map.of(
                "pausedAt", pausedAt.toString(),
                "resumedAt", now.toString(),
                "dueAt", dueAt.toString()
        )));
        return resumed;
    }

    /**
     * Resumes every PAUSED clock of the aggregate (idempotent when none are paused). The inverse
     * of {@link #pauseForState}: called when an aggregate leaves a pauseable business state.
     */
    @Transactional
    public List<SlaClock> resumeAll(UUID aggregateId, String actorId) {
        List<SlaClock> resumed = new ArrayList<>();
        for (SlaClock clock : clocks.findActiveByAggregate(aggregateId)) {
            if (clock.state() == SlaClock.State.PAUSED) {
                resumed.add(resume(clock.id(), actorId));
            }
        }
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

    /**
     * Detects warning windows for RUNNING clocks that reached warning_at but are not yet due.
     * Each clock is warned at most once (warned_at marker); emits {@code sla.warning} events.
     *
     * @return number of clocks warned
     */
    @Transactional
    public int detectWarnings(int batchSize) {
        Instant now = Instant.now();
        int count = 0;
        for (SlaClock clock : clocks.findDueForWarning(batchSize)) {
            clocks.markWarned(clock.id());
            clocks.appendHistory(clock.id(), "WARN", "system", writeDetails(Map.of(
                    "warningAt", clock.warningAt() == null ? "" : clock.warningAt().toString(),
                    "dueAt", clock.dueAt().toString(),
                    "detectedAt", now.toString()
            )));
            outbox.record(new DomainEvent(
                    UUID.randomUUID(),
                    "sla.warning",
                    1,
                    now,
                    UUID.randomUUID(),
                    "sla-clock",
                    clock.id().toString(),
                    Map.of(
                            "policyKey", clock.policyKey(),
                            "aggregateId", clock.aggregateId().toString(),
                            "metric", clock.metric(),
                            "warningAt", clock.warningAt() == null ? "" : clock.warningAt().toString(),
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
                null,
                state
        );
    }

    private static SlaClock withTiming(
            SlaClock clock,
            Instant dueAt,
            Instant warningAt,
            Instant pausedAt,
            SlaClock.State state
    ) {
        return new SlaClock(
                clock.id(), clock.policyKey(), clock.aggregateId(), clock.metric(),
                clock.startedAt(), dueAt, warningAt, pausedAt, state
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
