# SLA policy administration

SLA policy changes are tenant overrides of default definitions. Every PATCH carries the policy's
`expectedVersion`; concurrent or stale changes fail with HTTP 409 instead of silently overwriting
another administrator's work. First tenant override advances the inherited version, and later
updates use a guarded `WHERE version = ?` write.

Target durations must be positive and warning lead time non-negative. Successful changes append
an audit event and transactional outbox event. Runtime SLA clocks retain their own history and use
the active tenant policy/default fallback independently of admin UI state.
