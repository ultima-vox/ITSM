# Durable workflow timers

Workflow transitions may declare `timer: { delaySeconds, maxAttempts }`. Timer transitions are
system actions and therefore cannot require actor permissions, request fields, or approval.

Entering a state atomically cancels pending timers from the prior state and schedules timers for
the pinned workflow definition and instance version. PostgreSQL remains source of truth.

Workers claim due rows with `FOR UPDATE SKIP LOCKED` and a 60-second lease. Crashed workers are
reclaimed after lease expiry. Execution verifies tenant, instance identity, definition version,
and source instance version before applying the transition. Stale timers are cancelled. Failures
retry with bounded exponential backoff, then enter `DEAD` for operator inspection. Completed,
cancelled, and dead rows remain immutable history.
