# First vertical slice — operator work queue

## Acceptance criteria

1. A signed-in operator can list only work items they are authorized to see.
2. Result includes stable number, localized presentation metadata, status/SLA state and last update.
3. Every read/write is correlation-aware; writes create audit and outbox records atomically.
4. Russian is the default UI; English can be selected per user and persists independently.
5. Keyboard search, focus visibility, responsive layouts, empty state and semantic controls are provided.

The current UI is an interactive visual reference backed by local seeded data; the API foundation exposes the equivalent work-item read contract. Connecting it to OIDC/API storage is the next vertical increment.
