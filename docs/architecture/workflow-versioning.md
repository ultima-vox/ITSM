# Deterministic workflow versioning

Every workflow instance is pinned to `definition_version`. Transition evaluation loads
that exact version, including inactive historical definitions; activating a newer
definition affects only newly started instances.

Administrators migrate an existing instance through
`POST /api/v1/workflow/instances/{objectType}/{objectId}/migrations` or the Workflow
admin screen. Migration requires:

- `workflow.write`;
- explicit target definition version;
- caller's expected optimistic instance version;
- current state declared by target definition.

Migration preserves state, increments instance version, and writes audit plus
transactional outbox evidence. Missing target state or stale instance version returns
`409`; no implicit or partial migration occurs. `GET` on the same instance path exposes
current state, pinned definition version, and optimistic version for tooling.
