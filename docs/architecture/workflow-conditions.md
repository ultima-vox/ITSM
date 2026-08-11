# Workflow transition conditions

Transitions may declare an AND-list of typed conditions over command fields. Supported operators:
`EQUALS`, `NOT_EQUALS`, `IN`, `CONTAINS`, `EXISTS`, `GT`, `GTE`, `LT`, and `LTE`.

Field paths are bounded map-only paths. Evaluation never uses reflection, SpEL, scripts, or dynamic
code. Equality remains type-safe except numeric values, which compare through `BigDecimal` so JSON
integer/decimal representations behave consistently. Ordered comparisons accept numbers only.
Missing fields and type mismatches fail closed. `EXISTS: false` is explicit exception.

Conditions are version-pinned with workflow instance definition. All conditions must match before
approval, state update, audit, and outbox completion can succeed. Timer transitions may
not declare request-dependent conditions because background execution has no request payload.
