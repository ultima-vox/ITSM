# Architecture baseline

Vox ITSM is a **modular monolith**. PostgreSQL is authoritative; Redis, OpenSearch and UI projections are rebuildable. Modules may communicate only through published application contracts or domain events—not repositories or tables owned by another module.

## Module map

| Layer | Modules |
|---|---|
| Platform core | Object metadata, Form, Workflow, Automation, RBAC, SLA, Event/Outbox, Notification, Search, Audit |
| Business | Service Desk, Catalog, CMDB, Assets, Knowledge, Problem, Change |
| Isolated | AI Gateway (provider adapters; policy gate; no direct domain writes) |

Each mutation must execute authorization, validation, audit recording and transactional-outbox insertion in one transaction. Consumers are idempotent using event ID. RabbitMQ, OpenSearch, S3 and AI are optional dependencies with explicit timeout, retry and degraded-state handling.

## Public boundaries

- REST under `/api/v1`; OpenAPI is generated and versioned.
- Event names are past tense, namespaced and versioned in payload (`incident.created`, `v: 1`).
- The resource server validates Keycloak/OIDC JWTs. UI visibility is never an authorization control.
- Object-level decisions are evaluated server-side from role, scope, ownership and field policy.
